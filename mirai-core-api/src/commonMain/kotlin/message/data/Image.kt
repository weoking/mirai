/*
 * Copyright 2019-2021 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/dev/LICENSE
 */

@file:JvmMultifileClass
@file:JvmName("MessageUtils")

@file:Suppress(
    "EXPERIMENTAL_API_USAGE",
    "unused",
    "UnusedImport",
    "DEPRECATION_ERROR", "NOTHING_TO_INLINE", "MemberVisibilityCanBePrivate"
)

package net.mamoe.mirai.message.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import net.mamoe.kjbb.JvmBlockingBridge
import net.mamoe.mirai.Bot
import net.mamoe.mirai.IMirai
import net.mamoe.mirai.Mirai
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.contact.Contact.Companion.sendImage
import net.mamoe.mirai.contact.Contact.Companion.uploadImage
import net.mamoe.mirai.message.code.CodableMessage
import net.mamoe.mirai.message.data.Image.Factory
import net.mamoe.mirai.message.data.Image.Key.IMAGE_ID_REGEX
import net.mamoe.mirai.message.data.Image.Key.IMAGE_RESOURCE_ID_REGEX_1
import net.mamoe.mirai.message.data.Image.Key.IMAGE_RESOURCE_ID_REGEX_2
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import net.mamoe.mirai.utils.*
import net.mamoe.mirai.utils.ExternalResource.Companion.sendAsImageTo
import net.mamoe.mirai.utils.ExternalResource.Companion.uploadAsImage
import kotlin.LazyThreadSafetyMode.NONE

/**
 * 自定义表情 (收藏的表情) 和普通图片.
 *
 *
 * 最推荐的存储方式是存储图片原文件, 每次发送图片时都使用文件上传.
 * 在上传时服务器会根据其缓存情况回复已有的图片 ID 或要求客户端上传. 详见 [Contact.uploadImage]
 *
 * ### 根据 ID 构造图片
 * - [Image.fromId]. 在 Kotlin, 更推荐使用顶层函数 `val image = Image("id")`
 *
 * ### 上传和发送图片
 * - [Contact.uploadImage] 上传 [资源文件][ExternalResource] 并得到 [Image] 消息
 * - [Contact.sendImage] 上传 [资源文件][ExternalResource] 并发送返回的 [Image] 作为一条消息
 *
 * - [ExternalResource.uploadAsImage]
 * - [ExternalResource.sendAsImageTo]
 * - [Contact.sendImage]
 *
 * ### 下载图片
 * - [Image.queryUrl] 扩展函数. 查询图片下载链接
 * - [IMirai.queryImageUrl] 查询图片下载链接 (Java 使用)
 *
 * ## mirai 码支持
 * 格式: &#91;mirai:image:*[Image.imageId]*&#93;
 *
 * @see FlashImage 闪照
 * @see Image.flash 转换普通图片为闪照
 */
@Serializable(Image.Serializer::class)
@NotStableForInheritance
public interface Image : Message, MessageContent, CodableMessage {

    /**
     * 图片的 id.
     *
     * 图片 id 不一定会长时间保存, 也可能在将来改变格式, 因此不建议使用 id 发送图片.
     *
     * ### 格式
     * 所有图片的 id 都满足正则表达式 [IMAGE_ID_REGEX]. 示例: `{01E9451B-70ED-EAE3-B37C-101F1EEBF5B5}.ext` (ext 为文件后缀, 如 png)
     *
     * @see Image 使用 id 构造图片
     */
    public val imageId: String

    /**
     * 图片的宽度 (px), 当无法获取时为 0
     *
     * @since 2.8.0
     */
    public val width: Int

    /**
     * 图片的高度 (px), 当无法获取时为 0
     *
     * @since 2.8.0
     */
    public val height: Int

    /**
     * 图片的大小（字节）, 当无法获取时为 0. 可用于 [isUploaded].
     *
     * @since 2.8.0
     */
    public val size: Long

    /**
     * 图片的类型, 当无法获取时为未知 [ImageType.UNKNOWN]
     *
     * @since 2.8.0
     *
     * @see ImageType
     */
    public val imageType: ImageType

    /**
     * 判断该图片是否为 `动画表情`
     *
     * @since 2.8.0
     */
    public val isEmoji: Boolean get() = false

    /**
     * 图片文件 MD5. 可用于 [isUploaded].
     *
     * @return 16 bytes
     * @see isUploaded
     * @since 2.9.0
     */ // was an extension on Image before 2.9.0-M1.
    public val md5: ByteArray get() = calculateImageMd5ByImageId(imageId)

    public object AsStringSerializer : KSerializer<Image> by String.serializer().mapPrimitive(
        SERIAL_NAME,
        serialize = { imageId },
        deserialize = { Image(it) },
    )

    public object Serializer : KSerializer<Image> by FallbackSerializer("Image")

    @MiraiInternalApi
    public open class FallbackSerializer(serialName: String) : KSerializer<Image> by Delegate.serializer().map(
        buildClassSerialDescriptor(serialName) { element("imageId", String.serializer().descriptor) },
        serialize = { Delegate(imageId) },
        deserialize = { Image(imageId) },
    ) {
        @SerialName(SERIAL_NAME)
        @Serializable
        internal data class Delegate(
            val imageId: String
        )
    }

    /**
     * 用于构造 [Image] 实例.
     *
     * @see Builder
     * @since 2.9.0
     */
    public interface Factory {
        /**
         * 构造一个 [Image]. 注意, 由于没有提供 [Image.size], [Image.isUploaded] 总是会返回 `false`. 但这不会影响图片的发送.
         */
        public fun create(imageId: String): Image = create(imageId, 0)

        /**
         * 构造一个 [Image].
         */
        public fun create(
            imageId: String,
            size: Long,
        ): Image = create(imageId, size, ImageType.UNKNOWN)

        /**
         * 构造一个 [Image].
         */
        public fun create(
            imageId: String,
            size: Long,
            type: ImageType = ImageType.UNKNOWN,
        ): Image = create(imageId, size, type, 0, 0)

        /**
         * 构造一个 [Image].
         */
        public fun create(
            imageId: String,
            size: Long,
            type: ImageType = ImageType.UNKNOWN,
            width: Int = 0,
            height: Int = 0,
        ): Image = create(imageId, size, type, width, height, false)

        /**
         * 构造一个 [Image].
         */
        public fun create(
            imageId: String,
            size: Long,
            type: ImageType = ImageType.UNKNOWN,
            width: Int = 0,
            height: Int = 0,
            isEmoji: Boolean = false
        ): Image

        public companion object INSTANCE : Factory by loadService(
            Factory::class,
            "net.mamoe.mirai.internal.message.ImageFactoryImpl"
        )
    }


    /**
     * 用于构建 [Image] 实例.
     *
     * 示例:
     *
     * ```java
     * Builder builder = Image.Builder.newBuilder("{01E9451B-70ED-EAE3-B37C-101F1EEBF5B5}.jpg")
     * builder.setSize(123);
     * builder.setType(ImageType.PNG);
     *
     * Image image = builder.build();
     * ```
     *
     * @since 2.9.0
     * @see Factory
     */
    public class Builder private constructor(
        /**
         * @see Image.imageId
         */
        public var imageId: String,
    ) {
        /**
         * 图片大小字节数. 如果不提供改属性, 将无法 [Image.Key.isUploaded]
         *
         * @see Image.size
         */
        public var size: Long = 0
        public var type: ImageType = ImageType.UNKNOWN
        public var width: Int = 0
        public var height: Int = 0
        public var isEmoji: Boolean = false

        /**
         * 使用当前参数构造 [Image].
         */
        public fun build(): Image = Factory.create(
            imageId = imageId,
            size = size,
            type = type,
            width = width,
            height = height,
            isEmoji = isEmoji
        )

        public companion object {
            /**
             * 创建一个 [Builder]
             */
            @JvmStatic
            public fun newBuilder(imageId: String): Builder = Builder(imageId)
        }
    }

    @JvmBlockingBridge
    public companion object Key : AbstractMessageKey<Image>({ it.safeCast() }) {
        public const val SERIAL_NAME: String = "Image"

        /**
         * 通过 [Image.imageId] 构造一个 [Image] 以便发送.
         *
         * 图片 ID 不一定会长时间保存, 因此不建议使用 ID 发送图片. 建议使用 [Factory.create], 可以指定更多参数 (以及用于查询图片是否存在于服务器的必要参数 size).
         *
         * @see Image 获取更多说明
         * @see Image.imageId 获取更多说明
         * @see Factory.create
         */
        @JvmStatic
        public fun fromId(imageId: String): Image = Mirai.createImage(imageId)

        /**
         * 查询原图下载链接.
         *
         * - 当图片为从服务器接收的消息中的图片时, 可以直接获取下载链接, 本函数不会挂起协程.
         * - 其他情况下协程可能会挂起并向服务器查询下载链接, 或不挂起并拼接一个链接.
         *
         * @return 原图 HTTP 下载链接
         * @throws IllegalStateException 当无任何 [Bot] 在线时抛出 (因为无法获取相关协议)
         */
        @JvmStatic
        public suspend fun Image.queryUrl(): String {
            val bot = Bot.instancesSequence.firstOrNull() ?: error("No Bot available to query image url")
            return Mirai.queryImageUrl(bot, this)
        }

        /**
         * 当图片在服务器上存在时返回 `true`, 这意味着图片可以直接发送给 [contact].
         *
         * 若返回 `false`, 则图片需要用 [ExternalResource] 重新上传 ([Contact.uploadImage]).
         *
         * @since 2.9.0
         */
        @JvmStatic
        public suspend fun Image.isUploaded(bot: Bot): Boolean =
            InternalImageProtocol.instance.isUploaded(bot, md5, size, null, imageType, width, height)

        /**
         * 当图片在服务器上存在时返回 `true`, 这意味着图片可以直接发送给 [contact].
         *
         * 若返回 `false`, 则图片需要用 [ExternalResource] 重新上传 ([Contact.uploadImage]).
         *
         * @param md5 图片文件 MD5. 可通过 [Image.md5] 获得.
         * @param size 图片文件大小.
         *
         * @since 2.9.0
         */
        @JvmStatic
        public suspend fun isUploaded(
            bot: Bot,
            md5: ByteArray,
            size: Long,
        ): Boolean = InternalImageProtocol.instance.isUploaded(bot, md5, size, null)

        /**
         * 由 [Image.imageId] 计算 [Image.md5].
         *
         * @since 2.9.0
         */
        public fun calculateImageMd5ByImageId(imageId: String): ByteArray {
            @Suppress("DEPRECATION")
            return when {
                imageId matches IMAGE_ID_REGEX -> imageId.imageIdToMd5(1)
                imageId matches IMAGE_RESOURCE_ID_REGEX_2 -> imageId.imageIdToMd5(imageId.skipToSecondHyphen() + 1)
                imageId matches IMAGE_RESOURCE_ID_REGEX_1 -> imageId.imageIdToMd5(1)

                else -> throw IllegalArgumentException(
                    "Illegal imageId: '$imageId'. $ILLEGAL_IMAGE_ID_EXCEPTION_MESSAGE"
                )
            }
        }

        /**
         * 统一 ID 正则表达式
         *
         * `{01E9451B-70ED-EAE3-B37C-101F1EEBF5B5}.ext`
         */
        @Suppress("RegExpRedundantEscape") // This is required on Android
        @JvmStatic
        @get:JvmName("getImageIdRegex")
        public val IMAGE_ID_REGEX: Regex =
            Regex("""\{[0-9a-fA-F]{8}-([0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}\}\..{3,5}""")

        /**
         * 图片资源 ID 正则表达式 1. mirai 内部使用.
         *
         * `/f8f1ab55-bf8e-4236-b55e-955848d7069f`
         * @see IMAGE_RESOURCE_ID_REGEX_2
         */
        @JvmStatic
        @MiraiInternalApi
        @get:JvmName("getImageResourceIdRegex1")
        public val IMAGE_RESOURCE_ID_REGEX_1: Regex =
            Regex("""/[0-9a-fA-F]{8}-([0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}""")

        /**
         * 图片资源 ID 正则表达式 2. mirai 内部使用.
         *
         * `/000000000-3814297509-BFB7027B9354B8F899A062061D74E206`
         * @see IMAGE_RESOURCE_ID_REGEX_1
         */
        @JvmStatic
        @MiraiInternalApi
        @get:JvmName("getImageResourceIdRegex2")
        public val IMAGE_RESOURCE_ID_REGEX_2: Regex =
            Regex("""/[0-9]*-[0-9]*-[0-9a-fA-F]{32}""")
    }
}

/**
 * 通过 [Image.imageId] 构造一个 [Image] 以便发送.
 *
 * 图片 ID 不一定会长时间保存, 因此不建议使用 ID 发送图片. 建议使用 [Factory.create], 可以指定更多参数 (以及用于查询图片是否存在于服务器的必要参数 size).
 *
 * @see Image 获取更多关于 [Image] 的说明
 * @see Image.Factory 获取更多关于构造 [Image] 的方法
 *
 * @see IMirai.createImage
 */
@JvmSynthetic
public inline fun Image(imageId: String): Image = Factory.create(imageId)

/**
 * 通过 [Image.imageId] 构造一个 [Image] 以便发送.
 *
 * 图片 ID 不一定会长时间保存, 因此不建议使用 ID 发送图片. 建议使用 [Factory.create], 可以指定更多参数 (以及用于查询图片是否存在于服务器的必要参数 size).
 *
 * @see Image 获取更多关于 [Image] 的说明
 * @see Image.Factory 获取更多关于构造 [Image] 的方法
 *
 * @see IMirai.createImage
 * @since 2.9.0
 */
@JvmSynthetic
public inline fun Image(
    imageId: String,
    size: Long,
    type: ImageType = ImageType.UNKNOWN,
    width: Int = 0,
    height: Int = 0,
    isEmoji: Boolean = false
): Image = Factory.create(imageId, size, type, width, height, isEmoji)

public enum class ImageType(
    /**
     * @since 2.9.0
     */
    @MiraiInternalApi public val formatName: String,
) {
    PNG("png"),
    BMP("bmp"),
    JPG("jpg"),
    GIF("gif"),
    //WEBP, //Unsupported by pc client
    APNG("png"),
    UNKNOWN("gif"); // bad design, should use `null` to represent unknown, but we cannot change it anymore.

    public companion object {
        private val IMAGE_TYPE_ENUM_LIST = values()

        @JvmStatic
        public fun match(str: String): ImageType {
            return matchOrNull(str) ?: UNKNOWN
        }

        @JvmStatic
        public fun matchOrNull(str: String): ImageType? {
            val input = str.uppercase()
            return IMAGE_TYPE_ENUM_LIST.firstOrNull { it.name == input }
        }
    }
}

///////////////////////////////////////////////////////////////////////////
// Internals
///////////////////////////////////////////////////////////////////////////

@Deprecated("Use member function", level = DeprecationLevel.HIDDEN) // safe since it was internal
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
@MiraiInternalApi
@get:JvmName("calculateImageMd5")
public val Image.md5: ByteArray
    get() = Image.calculateImageMd5ByImageId(imageId)


/**
 * 所有 [Image] 实现的基类.
 */
@MiraiInternalApi
public sealed class AbstractImage : Image {
    private val _stringValue: String? by lazy(NONE) { "[mirai:image:$imageId]" }

    override val size: Long
        get() = 0L
    override val width: Int
        get() = 0
    override val height: Int
        get() = 0
    override val imageType: ImageType
        get() = ImageType.UNKNOWN

    final override fun toString(): String = _stringValue!!
    final override fun contentToString(): String = if (isEmoji) {
        "[动画表情]"
    } else {
        "[图片]"
    }

    override fun appendMiraiCodeTo(builder: StringBuilder) {
        builder.append("[mirai:image:").append(imageId).append("]")
    }

    final override fun hashCode(): Int = imageId.hashCode()
    final override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is Image) return false
        return this.imageId == other.imageId
    }
}


/**
 * 好友图片
 *
 * [imageId] 形如 `/f8f1ab55-bf8e-4236-b55e-955848d7069f` (37 长度)  或 `/000000000-3814297509-BFB7027B9354B8F899A062061D74E206` (54 长度)
 */
// NotOnlineImage
@MiraiInternalApi
public abstract class FriendImage @MiraiInternalApi public constructor() :
    AbstractImage() { // change to sealed in the future.
    public companion object
}

/**
 * 群图片.
 *
 * @property imageId 形如 `{01E9451B-70ED-EAE3-B37C-101F1EEBF5B5}.ext` (ext系扩展名)
 * @see Image 查看更多说明
 */
// CustomFace
@MiraiInternalApi
public abstract class GroupImage @MiraiInternalApi public constructor() :
    AbstractImage() { // change to sealed in the future.
    public companion object
}


/**
 * 内部图片协议实现
 * @since 2.9.0-M1
 */
@MiraiInternalApi
public interface InternalImageProtocol { // naming it Internal* to assign it a lower priority when resolving Image*
    /**
     * @param context 用于检查的 [Contact]. 群图片与好友图片是两个通道, 建议使用欲发送到的 [Contact] 对象作为 [contact] 参数, 但目前不提供此参数时也可以检查.
     */
    public suspend fun isUploaded(
        bot: Bot,
        md5: ByteArray,
        size: Long,
        context: Contact? = null,
        type: ImageType = ImageType.UNKNOWN,
        width: Int = 0,
        height: Int = 0
    ): Boolean

    @MiraiInternalApi
    public companion object {
        public val instance: InternalImageProtocol by lazy {
            loadService(
                InternalImageProtocol::class,
                "net.mamoe.mirai.internal.message.InternalImageProtocolImpl"
            )
        }
    }
}
