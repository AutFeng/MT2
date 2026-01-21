package bin.mt2.plus.utils

import java.io.File

/**
 * 路径工具类
 * 用于处理零宽字符路径转换，绕过Android系统对特定目录的访问限制
 */
object PathUtils {
    
    // 零宽字符常量
    private const val ZERO_WIDTH_SPACE = "\u200B"           // 零宽空格
    private const val ZERO_WIDTH_NON_JOINER = "\u200C"      // 零宽非连接符
    private const val ZERO_WIDTH_JOINER = "\u200D"          // 零宽连接符
    
    // 受限制的路径关键词
    private val RESTRICTED_PATHS = listOf(
        "Android/data",
        "Android/obb",
        "android/data",
        "android/obb"
    )
    
    /**
     * 将普通路径转换为带零宽字符的路径
     * 用于绕过系统限制访问Android目录
     * 
     * @param path 原始路径
     * @return 转换后的路径
     */
    fun addZeroWidthChar(path: String): String {
        var result = path
        
        // 检查路径是否包含受限制的关键词
        RESTRICTED_PATHS.forEach { restrictedPath ->
            if (path.contains(restrictedPath, ignoreCase = true)) {
                // 在"Android"后面插入零宽字符
                result = result.replace(
                    Regex("(Android)", RegexOption.IGNORE_CASE)
                ) { matchResult ->
                    matchResult.value + ZERO_WIDTH_SPACE
                }
            }
        }
        
        return result
    }
    
    /**
     * 移除路径中的零宽字符
     * 用于显示和存储真实路径
     * 
     * @param path 带零宽字符的路径
     * @return 清理后的路径
     */
    fun removeZeroWidthChar(path: String): String {
        // 移除所有零宽字符和不可见字符
        return path.replace(Regex("[\u200B-\u200D\uFEFF\u180E]"), "")
    }
    
    /**
     * 检查路径是否为受限路径
     * 
     * @param path 要检查的路径
     * @return 是否为受限路径
     */
    fun isRestrictedPath(path: String): Boolean {
        val cleanPath = removeZeroWidthChar(path)
        return RESTRICTED_PATHS.any { 
            cleanPath.contains(it, ignoreCase = true) 
        }
    }
    
    /**
     * 获取文件对象，自动处理零宽字符
     * 
     * @param path 文件路径
     * @return File对象
     */
    fun getFile(path: String): File {
        val processedPath = if (isRestrictedPath(path)) {
            addZeroWidthChar(path)
        } else {
            path
        }
        return File(processedPath)
    }
    
    /**
     * 列出目录下的文件，自动处理零宽字符
     * 
     * @param path 目录路径
     * @return 文件列表
     */
    fun listFiles(path: String): Array<File>? {
        val file = getFile(path)
        return file.listFiles()
    }
    
    /**
     * 检查文件是否存在，自动处理零宽字符
     * 
     * @param path 文件路径
     * @return 文件是否存在
     */
    fun exists(path: String): Boolean {
        return getFile(path).exists()
    }
    
    /**
     * 检查是否为目录，自动处理零宽字符
     * 
     * @param path 文件路径
     * @return 是否为目录
     */
    fun isDirectory(path: String): Boolean {
        return getFile(path).isDirectory
    }
}
