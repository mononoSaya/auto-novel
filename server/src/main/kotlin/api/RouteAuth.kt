package api

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import infra.common.UserRepository
import infra.model.User
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.resources.post
import io.ktor.server.routing.*
import jakarta.mail.internet.AddressException
import jakarta.mail.internet.InternetAddress
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import util.Email
import util.PBKDF2
import java.util.*
import kotlin.time.Duration.Companion.days

@Resource("/auth")
private class AuthRes {
    @Resource("/sign-in")
    class SignIn(val parent: AuthRes = AuthRes())

    @Resource("/sign-up")
    class SignUp(val parent: AuthRes = AuthRes())

    @Resource("/verify-email")
    class VerifyEmail(
        val parent: AuthRes = AuthRes(),
        val email: String,
    )

    @Resource("/reset-password-email")
    class ResetPasswordEmail(val parent: AuthRes = AuthRes(), val emailOrUsername: String)

    @Resource("/reset-password")
    class ResetPassword(val parent: AuthRes = AuthRes(), val emailOrUsername: String)
}

fun Route.routeAuth() {
    val service by inject<AuthApi>()

    post<AuthRes.SignIn> {
        @Serializable
        class Body(
            val emailOrUsername: String,
            val password: String,
        )

        val body = call.receive<Body>()
        val result = service.signIn(
            emailOrUsername = body.emailOrUsername,
            password = body.password,
        )
        call.respondResult(result)
    }

    post<AuthRes.SignUp> {
        @Serializable
        class Body(
            val email: String,
            val emailCode: String,
            val username: String,
            val password: String,
        )

        val body = call.receive<Body>()
        val result = service.signUp(
            email = body.email,
            emailCode = body.emailCode,
            username = body.username,
            password = body.password,
        )
        call.respondResult(result)
    }
    post<AuthRes.VerifyEmail> { loc ->
        val result = service.sendVerifyEmail(loc.email)
        call.respondResult(result)
    }

    post<AuthRes.ResetPasswordEmail> { loc ->
        val result = service.sendResetPasswordTokenEmail(loc.emailOrUsername)
        call.respondResult(result)
    }
    post<AuthRes.ResetPassword> { loc ->
        @Serializable
        class Body(
            val token: String,
            val password: String,
        )

        val body = call.receive<Body>()
        val result = service.resetPassword(
            emailOrUsername = loc.emailOrUsername,
            token = body.token,
            password = body.password,
        )
        call.respondResult(result)
    }
}

class AuthApi(
    private val secret: String,
    private val userRepository: UserRepository,
) {
    private fun generateToken(
        username: String,
        role: User.Role,
    ): Pair<String, Long> {
        val expiresAt = (Clock.System.now() + 180.days)
        return Pair(
            JWT.create()
                .apply {
                    withClaim("username", username)
                    if (role != User.Role.Normal) {
                        withClaim("role", role.toString())
                    }
                    withExpiresAt(expiresAt.toJavaInstant())
                }
                .sign(Algorithm.HMAC256(secret)),
            expiresAt.epochSeconds,
        )
    }

    @Serializable
    class SignInDto(
        val email: String,
        val username: String,
        val role: User.Role,
        val token: String,
        val expiresAt: Long,
    )

    suspend fun signIn(
        emailOrUsername: String,
        password: String,
    ): Result<SignInDto> {
        val user = userRepository.getByEmail(emailOrUsername)
            ?: userRepository.getByUsername(emailOrUsername)
            ?: return httpNotFound("用户不存在")

        fun User.validatePassword(password: String): Boolean {
            return this.password == PBKDF2.hash(password, salt)
        }
        if (!user.validatePassword(password))
            return httpUnauthorized("密码错误")

        val (token, expiresAt) = generateToken(user.username, user.role)
        return Result.success(
            SignInDto(
                email = user.email,
                username = user.username,
                role = user.role,
                token = token,
                expiresAt = expiresAt,
            )
        )
    }

    suspend fun signUp(
        email: String,
        emailCode: String,
        username: String,
        password: String,
    ): Result<SignInDto> {
        if (username.length < 3) {
            return httpBadRequest("用户名至少为3个字符")
        }
        if (username.length > 15) {
            return httpBadRequest("用户名至多为15个字符")
        }
        if (password.length < 8) {
            return httpBadRequest("密码至少为8个字符")
        }
        userRepository.getByEmail(email)?.let {
            return httpConflict("邮箱已经被使用")
        }
        userRepository.getByUsername(username)?.let {
            return httpConflict("用户名已经被使用")
        }

        if (!userRepository.validateEmailCode(email, emailCode))
            return httpBadRequest("邮箱验证码错误")

        userRepository.add(
            email = email,
            username = username,
            password = password,
        )

        val (token, expiresAt) = generateToken(username, User.Role.Normal)
        return Result.success(
            SignInDto(
                email = email,
                username = username,
                role = User.Role.Normal,
                token = token,
                expiresAt = expiresAt,
            )
        )
    }

    suspend fun sendVerifyEmail(email: String): Result<Unit> {
        userRepository.getByEmail(email)?.let {
            return httpConflict("邮箱已经被使用")
        }

        try {
            InternetAddress(email).apply { validate() }
        } catch (e: AddressException) {
            return httpBadRequest("邮箱不合法")
        }

        val emailCode = String.format("%06d", Random().nextInt(999999))

        try {
            Email.send(
                to = email,
                subject = "$emailCode 日本网文机翻机器人 注册激活码",
                text = "您的注册激活码为 $emailCode\n" +
                        "激活码将会在15分钟后失效,请尽快完成注册\n" +
                        "这是系统邮件，请勿回复"
            )
        } catch (e: AddressException) {
            return httpInternalServerError("邮件发送失败")
        }

        userRepository.addEmailCode(email, emailCode)
        return Result.success(Unit)
    }

    suspend fun sendResetPasswordTokenEmail(emailOrUsername: String): Result<String> {
        val user = userRepository.getByUsernameOrEmail(emailOrUsername)
            ?: return httpNotFound("用户不存在")

        try {
            InternetAddress(user.email).apply { validate() }
        } catch (e: AddressException) {
            return httpBadRequest("邮箱不合法")
        }

        val token = UUID.randomUUID().toString()

        try {
            Email.send(
                to = user.email,
                subject = "日本网文机翻机器人 重置密码口令",
                text = "您的重置密码口令为 $token\n" +
                        "口令将会在15分钟后失效,请尽快重置密码\n" +
                        "如果发送了多个口令，请使用最新的口令，旧的口令将失效\n" +
                        "这是系统邮件，请勿回复"
            )
        } catch (e: AddressException) {
            return httpInternalServerError("邮件发送失败")
        }

        userRepository.addResetPasswordToken(user.id, token)
        return Result.success("邮件已发送")
    }

    suspend fun resetPassword(
        emailOrUsername: String,
        token: String,
        password: String,
    ): Result<Unit> {
        val user = userRepository.getByUsernameOrEmail(emailOrUsername)
            ?: return httpNotFound("用户不存在")
        if (!userRepository.validateResetPasswordToken(user.id, token)) {
            return httpBadRequest("口令不合法")
        }
        userRepository.updatePassword(user.id, password)
        return Result.success(Unit)
    }
}
