package bin.mt2.plus.model

import java.io.File

/**
 * 自定义文件数据模型
 *
 * @property file 文件对象
 * @property isParent 是否为父目录（..）
 */
data class CustomFile(val file: File, val isParent: Boolean)
