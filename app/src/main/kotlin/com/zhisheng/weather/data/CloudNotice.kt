package com.zhisheng.weather.data

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.zhisheng.weather.BuildConfig
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** 一条云端公告。 */
internal data class CloudNotice(
    val id: Long,
    val title: String,
    val body: String,
    val publishedAt: String,
    val pinned: Boolean,
)

/**
 * 小白网络验证（xbzhan）的公告接口。
 *
 * 通讯使用对方平台配置的 RSA2：请求体里的 data 用私钥做 PKCS#1 v1.5 填充后按模幂加密，
 * 响应的 data 是若干 256 字节分组，用公钥指数按模幂解出后剥掉填充即为 JSON。
 * 平台不提供编译期常量，所以这里直接实现 RSA，不引入额外加密库。
 */
internal object CloudNoticeRepository {

    private const val SOFT = "5Msh2lJ9wr02fesYtx"

    /** api2 不可用时自动切 api3（平台建议的线路降级）。 */
    private val hosts = listOf("https://api2.xbzhan.com", "https://api3.xbzhan.com")

    private const val PRIVATE_KEY_PEM = """
-----BEGIN PRIVATE KEY-----
MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC8IQEcQTXYr5Fb
QU4IrWagUtHP+M2PYw3CF23JSQnYgQkVAQbNJfF6O+H7AkuuDZQlVxKgDHi6Ixrr
VCv+db+nr5Nd2eP+Y1iFKiLTscpunffvhJIEsADok3kR7yRJiRRt+Izb4/VH7zm+
OS2uvYSX0BtumYzl+sdi/CirjwVS5N3j+lSt0Ok+r2/rjEYZ8f2C+rtKkMjPs5E7
Jmz2+UDPqrh2+gFV8MIoCjNqQbpmW9Xq0vN1kUljae2cEcKuWbpDaB75FWjjyXlt
vBK5xWpmiukqKvbFuAHhLFcuVfrndq8OA3v+xijylp3goqmORBh4H/nYT9hQZ4/l
sK7E1FihAgMBAAECggEAXkf8Enjrz2hNZHpZ7SdZo0deNuftvMpbfsXXE2UzNfmU
353gFrfJSQTxv6Xq35OWhxAYGXWmm0I0n3NTiPbh0du/8uzbwgVvu2zef1E+rikk
tDiwJQE/s08VAdPjT6cPQf2qOhkIN8DAI4DsUAJSjPKSjN8PKcKAYi8VAsVuWZfT
Tw867BncgYRqXonuBpNVz7JUYpdcGx4WFmxWqkvDGDs6iHBE7bVyjVKP3qpX1E/D
DuwvEGpOKeeET9WPSAmYxKE/F3DosJd3vdoKrx5QSsj1xf6yUdSbnR0Y6lgM4lav
SWLTWEbBtGgv/9s5LX8EajavdchnFyS4v199Pwmp8QKBgQD7ToPi8Mfi9TIfuijM
czFYjO/vHpr3x3qXX4co0x3wZ+vAyVzxSM73C3oRPtA1xekhmjFUKCm+zr0hb1iW
NOLzQwiH/3M6TeQsDGsIJdwJ48o1v69/x0quuItytDOOwX8Gf30H+d9CxvomBZBg
9kv1qVqPb6+t6uD1iX49IClMiwKBgQC/pHB5Ud8V+WsDSSTFwI6Z5ou3dmoelTjf
gj961hye2S73pkvNkKbzhwaxY/OKb7B45oohRwiVzZekTbHaluOMznkwVTPP2yPf
j+HTikUbObGTR6c0ZOqd4NIbMsLebVYlNF/BiKx06IiC5G5H5vDbTsM4XWcah9qY
tP0//Ri5AwKBgD4ewmsuqbpoceN12O/w6ZnB/O1W22e0X5Fbg8WLqjLq/VPCcr8S
CFb490ucA0vHiEFOJDNxWiBYalgBdhxrJk6Lm2hCtPEMoAJjb3GsKiAntqxinbcw
RD28UHwiHzyCiCvLB9UY5x9SoDMV3QKa31vyaPhb20labhFD03i4onzRAoGAI6AX
BIaSuQwHt7GmM1jLaZKxEGbj7VGCJBKbaGNbKLBpCcKRPuKQFp9YQCu7f1Wp8Z+x
t+PzlZ2FfhavZFo+bNOJNdHw8sfjEFE4AtImbJw6BY4BaD0GXFkJ1eKG1UBwDjSB
RIH6xTThA6whUB5/UdV/dv/uBcGRgmfK2FVdfi8CgYEAkR2taNj7UopsVCn4ifz8
gqtWHLgV6/nDNEaLRquSskquL/8goHUwtjoEL/Xn31zj97dU/o4f7A5sdOCIIXAD
FfZhVbfIhVWFEfocjb7zh51RMnEmDMqkwQdIqwJ6boDmS7CJWMt51w3akEzbMFDY
xS6FTfc266ywQFdxtkJIW28=
-----END PRIVATE KEY-----
"""

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val rsa = Rsa(privateKeyPem = PRIVATE_KEY_PEM)
    private val random = SecureRandom()

    /** 进程内客户端 id：一次启动只生成一次。 */
    private val clientId = randomToken()

    /** 最近一次成功请求的公告服务源（用于解析公告正文里的相对图片路径）。 */
    @Volatile
    internal var noticeOrigin: String = hosts.first()
        private set

    @Volatile
    private var cache: List<CloudNotice>? = null

    /** 取公告列表；[force] 为 true 时跳过快照重新请求。 */
    suspend fun notices(context: Context, force: Boolean = false): List<CloudNotice> =
        withContext(Dispatchers.IO) {
            cache?.takeIf { !force }?.let { return@withContext it }
            val mac = deviceCode(context.applicationContext)
            val version = BuildConfig.VERSION_NAME
            val clientos = "Android ${Build.VERSION.RELEASE}"

            val init = request(
                path = "/api/init",
                payload = linkedMapOf(
                    "uuid" to randomToken(),
                    "token" to randomToken(),
                    "clientid" to clientId,
                    "version" to version,
                    "mac" to mac,
                    "feature" to mac,
                    "clientos" to clientos,
                ),
            )
            val list = request(
                path = "/api/notice",
                payload = linkedMapOf(
                    "uuid" to (init["uuid"]?.jsonPrimitive?.contentOrNull ?: randomToken()),
                    "token" to (init["token"]?.jsonPrimitive?.contentOrNull ?: randomToken()),
                    "clientid" to clientId,
                    "version" to version,
                    "mac" to mac,
                    "feature" to mac,
                    "clientos" to clientos,
                ),
            )
            val items = parseNotices(list["result"])
            cache = items
            items
        }

    private fun parseNotices(element: kotlinx.serialization.json.JsonElement?): List<CloudNotice> {
        val array = element as? JsonArray ?: return emptyList()
        return array.mapNotNull { row ->
            val obj = row.jsonObject
            val title = obj["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (title.isEmpty()) return@mapNotNull null
            CloudNotice(
                id = obj["id"]?.jsonPrimitive?.longOrNull ?: 0L,
                title = title,
                body = obj["info"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                publishedAt = obj["createTime"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                pinned = (obj["top"]?.jsonPrimitive?.intOrNull ?: 0) == 1,
            )
        }.sortedByDescending { it.pinned }
    }

    /** 发一次封包：data 用私钥加密，返回解密后的 JSON。 */
    private fun request(path: String, payload: Map<String, String>): JsonObject {
        val plain = buildString {
            append('{')
            payload.entries.forEachIndexed { index, (key, value) ->
                if (index > 0) append(',')
                append(JsonPrimitive(key)).append(':').append(JsonPrimitive(value))
            }
            append('}')
        }
        val body = buildString {
            append('{')
            append("\"soft\":").append(JsonPrimitive(SOFT)).append(',')
            append("\"data\":").append(
                JsonPrimitive(Base64.getEncoder().encodeToString(rsa.encrypt(plain.toByteArray()))),
            ).append(',')
            append("\"sign\":\"\"}")
        }
        var last: Exception? = null
        for (host in hosts) {
            try {
                val call = Request.Builder()
                    .url(host + path)
                    .post(body.toByteArray().toRequestBody(JSON_MEDIA))
                    .build()
                client.newCall(call).execute().use { response ->
                    if (!response.isSuccessful) error("公告服务返回 ${response.code}")
                    val text = response.body?.string().orEmpty()
                    return decode(text).also { noticeOrigin = host }
                }
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("公告服务不可用")
    }

    private fun decode(raw: String): JsonObject {
        val root = json.parseToJsonElement(raw).jsonObject
        if (root["status"]?.jsonPrimitive?.contentOrNull != "success") {
            error(root["msg"]?.jsonPrimitive?.contentOrNull ?: "公告服务返回异常")
        }
        val data = Base64.getDecoder().decode(root.getValue("data").jsonPrimitive.content)
        val plain = ByteArrayOutputStream()
        var offset = 0
        while (offset < data.size) {
            val end = minOf(offset + rsa.blockSize, data.size)
            plain.write(rsa.decrypt(data.copyOfRange(offset, end)))
            offset = end
        }
        return json.parseToJsonElement(plain.toString(Charsets.UTF_8.name())).jsonObject
    }

    /** 设备标识：优先 Android ID，缺失时退回一次性随机值。 */
    private fun deviceCode(context: Context): String {
        val id = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()
        return id?.takeIf { it.length >= 8 } ?: randomToken()
    }

    private fun randomToken(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return buildString(16) { repeat(16) { append(chars[random.nextInt(chars.length)]) } }
    }

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    /**
     * 最小 RSA：平台约定的加密方式是「私钥加密、公钥解密」，Java 的 Cipher 不接受这种方向，
     * 所以直接用 BigInteger 做模幂并自己处理 PKCS#1 v1.5 填充（请求 type 1，响应 type 1 剥除）。
     */
    private class Rsa(privateKeyPem: String) {
        private val modulus: BigInteger
        private val privateExponent: BigInteger
        private val publicExponent: BigInteger

        val blockSize: Int

        init {
            val base64 = privateKeyPem.lineSequence()
                .filterNot { it.startsWith("-----") }
                .joinToString("")
            val key = KeyFactory.getInstance("RSA")
                .generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)))
                as java.security.interfaces.RSAPrivateCrtKey
            modulus = key.modulus
            privateExponent = key.privateExponent
            publicExponent = key.publicExponent
            blockSize = (modulus.bitLength() + 7) / 8
        }

        fun encrypt(plain: ByteArray): ByteArray {
            require(plain.size <= blockSize - 11) { "公告请求过长" }
            val padding = ByteArray(blockSize - plain.size - 3) { 0xFF.toByte() }
            val block = byteArrayOf(0, 1) + padding + byteArrayOf(0) + plain
            return BigInteger(1, block).modPow(privateExponent, modulus).fixed(blockSize)
        }

        fun decrypt(cipher: ByteArray): ByteArray {
            val block = BigInteger(1, cipher).modPow(publicExponent, modulus).fixed(blockSize)
            require(block.size >= 11 && block[0].toInt() == 0 && block[1].toInt() == 1) { "公告响应填充异常" }
            var index = 2
            while (index < block.size && block[index] == 0xFF.toByte()) index++
            require(index < block.size && block[index].toInt() == 0) { "公告响应填充异常" }
            return block.copyOfRange(index + 1, block.size)
        }

        private fun BigInteger.fixed(size: Int): ByteArray {
            val bytes = toByteArray()
            return when {
                bytes.size == size -> bytes
                bytes.size > size -> bytes.copyOfRange(bytes.size - size, bytes.size)
                else -> ByteArray(size - bytes.size) + bytes
            }
        }
    }
}
