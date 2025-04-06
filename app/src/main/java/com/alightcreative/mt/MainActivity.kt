package com.alightcreative.mt

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.StatFs
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import java.io.File

class MainActivity : AppCompatActivity(), FileAdapter.OnItemClickListener {

    private lateinit var currentPathTextView: TextView
    private lateinit var storageInfoTextView: TextView
    private lateinit var TextBar: LinearLayout
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var menuIcon: ImageView
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var leftPath: String
    private lateinit var rightPath: String

    private var isInitialized = false

    private var leftFolderCount = 0
    private var leftFileCount = 0
    private var rightFolderCount = 0
    private var rightFileCount = 0

    private var permissionDialog: AlertDialog? = null

    @SuppressLint("MissingInflatedId", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 设置导航栏和状态栏颜色
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.navigationBarColor = ContextCompat.getColor(this, R.color.white)
            window.statusBarColor = ContextCompat.getColor(this, R.color.toolbar)
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }

        // 初始化视图
        currentPathTextView = findViewById(R.id.currentPathTextView)
        storageInfoTextView = findViewById(R.id.storageInfoTextView)
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        menuIcon = findViewById(R.id.menuIcon)
        TextBar = findViewById(R.id.TextBar)
        drawerToggle = ActionBarDrawerToggle(this, drawerLayout, R.string.drawer_open, R.string.drawer_close)
        drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()

        menuIcon.setOnClickListener {
            if (drawerLayout.isDrawerOpen(navigationView)) {
                drawerLayout.closeDrawer(navigationView)
            } else {
                drawerLayout.openDrawer(navigationView)
            }
        }

        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
            override fun onDrawerOpened(drawerView: View) {
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
            }
            override fun onDrawerClosed(drawerView: View) {
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            }
            override fun onDrawerStateChanged(newState: Int) {}
        })

        // 为 currentPathTextView 添加点击事件
        TextBar.setOnClickListener {
            val isLeft = determineIfLeft()
            showEditPathDialog(this, currentPathTextView.text.toString(), isLeft)
        }
    }

    private fun determineIfLeft(): Boolean {
        return currentPathTextView.text.toString() == leftPath
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun showEditPathDialog(context: Context, currentPath: String, isLeft: Boolean) {
        // Create a container with padding
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(context), 0, 24.dpToPx(context), 0) // Left and right padding
        }

        val editText = EditText(context).apply {
            setText(currentPath)
            setSingleLine(true)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, // Width to fill the container
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            highlightColor = ContextCompat.getColor(context, R.color.DialogSelectedColor)
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.DialogButtonColor))
            // Set cursor color
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                textCursorDrawable = ContextCompat.getDrawable(context, R.drawable.cursor_color)
            } else {
                try {
                    val f = TextView::class.java.getDeclaredField("mCursorDrawableRes")
                    f.isAccessible = true
                    f.set(this, R.drawable.cursor_color)
                } catch (ignored: Exception) {
                }
            }
        }

        container.addView(editText)

        // Create the dialog
        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.jump_to_path_title)
            .setView(container)
            .setPositiveButton(R.string.ok) { _, _ ->
                // Handle the OK button click
                val newPath = editText.text.toString()
                if (isLeft) {
                    leftPath = newPath
                    refreshList(true, newPath)
                } else {
                    rightPath = newPath
                    refreshList(false, newPath)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.paste) { _, _ ->
                // Handle the Paste button click
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipData = clipboard.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val pasteText = clipData.getItemAt(0).text.toString()
                    editText.setText(pasteText as CharSequence)
                    editText.setSelection(pasteText.length) // Move cursor to the end
                }
            }
            .create()

        // Set the button text colors
        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            val neutralButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            positiveButton.setTextColor(ContextCompat.getColor(context, R.color.DialogButtonColor))
            negativeButton.setTextColor(ContextCompat.getColor(context, R.color.DialogButtonColor))
            neutralButton.setTextColor(ContextCompat.getColor(context, R.color.DialogButtonColor))

            // Show the soft keyboard and select all text
            editText.requestFocus()
            editText.selectAll()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            Handler().postDelayed({
                imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
            }, 100)
        }

        // Show the dialog
        dialog.show()
    }

    private fun refreshList(isLeft: Boolean, newPath: String) {
        val recyclerView: RecyclerView = if (isLeft) findViewById(R.id.recyclerViewLeft) else findViewById(R.id.recyclerViewRight)
        val files = getFilesFromDirectory(newPath)
        val adapter = FileAdapter(addParentDirectory(files, newPath), this, isLeft)
        recyclerView.adapter = adapter
        updateFolderAndFileCount(newPath, isLeft)
        updateCurrentPath(newPath)
        setStorageInfo(newPath)
    }

    // 扩展函数：dp转px
    private fun Int.dpToPx(context: Context): Int = (this * context.resources.displayMetrics.density).toInt()

    // 显示权限请求对话框
    @RequiresApi(Build.VERSION_CODES.R)
    private fun showPermissionDialog() {
        if (permissionDialog?.isShowing == true) {
            return
        }

        val builder = AlertDialog.Builder(this)
        permissionDialog = builder
            .setTitle(R.string.authorization_dialog_title)
            .setMessage(R.string.authorization_dialog_message)
            .setPositiveButton(R.string.ok) { _, _ ->
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
                initializeFileLists()
            }
            .create()

        permissionDialog?.setOnShowListener {
            val positiveButton = permissionDialog?.getButton(AlertDialog.BUTTON_POSITIVE)
            val negativeButton = permissionDialog?.getButton(AlertDialog.BUTTON_NEGATIVE)
            positiveButton?.setTextColor(ContextCompat.getColor(this, R.color.DialogButtonColor))
            negativeButton?.setTextColor(ContextCompat.getColor(this, R.color.DialogButtonColor))
        }

        permissionDialog?.setCancelable(false)
        permissionDialog?.setCanceledOnTouchOutside(false)
        permissionDialog?.show()
    }

    override fun onResume() {
        super.onResume()
        // 检查权限是否已授予
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                if (!isInitialized) {
                    initializeFileLists()
                } else {
                    refreshList(determineIfLeft(), currentPathTextView.text.toString())
                }
                permissionDialog?.dismiss()
            } else {
                showPermissionDialog()
            }
        }
    }

    // 初始化文件列表
    @SuppressLint("ClickableViewAccessibility")
    private fun initializeFileLists() {
        val recyclerViewLeft: RecyclerView = findViewById(R.id.recyclerViewLeft)
        recyclerViewLeft.layoutManager = LinearLayoutManager(this)
        val filesLeft = getFilesFromDirectory("/storage/emulated/0/")
        val adapterLeft = FileAdapter(addParentDirectory(filesLeft, "/storage/emulated/0/"), this, true)
        recyclerViewLeft.adapter = adapterLeft
        leftPath = "/storage/emulated/0/"
        updateFolderAndFileCount(leftPath, true)

        val recyclerViewRight: RecyclerView = findViewById(R.id.recyclerViewRight)
        recyclerViewRight.layoutManager = LinearLayoutManager(this)
        val filesRight = getFilesFromDirectory("/storage/emulated/0/")
        val adapterRight = FileAdapter(addParentDirectory(filesRight, "/storage/emulated/0/"), this, false)
        recyclerViewRight.adapter = adapterRight
        rightPath = "/storage/emulated/0/"
        updateFolderAndFileCount(rightPath, false)

        // Add scroll and touch listeners
        recyclerViewLeft.addOnScrollListener(createScrollListener(true))
        recyclerViewRight.addOnScrollListener(createScrollListener(false))
        recyclerViewLeft.setOnTouchListener(createTouchListener(true))
        recyclerViewRight.setOnTouchListener(createTouchListener(false))

        updateCurrentPath("/storage/emulated/0/")
        setStorageInfo("/storage/emulated/0/")

        // Set the initialization flag to true
        isInitialized = true
    }

    // 创建滚动监听器
    private fun createScrollListener(isLeft: Boolean): RecyclerView.OnScrollListener {
        return object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy != 0) {
                    if (isLeft) {
                        currentPathTextView.text = leftPath
                        storageInfoTextView.text = getString(R.string.storage_info_text, leftFolderCount, leftFileCount, getStorageInfo(leftPath))
                    } else {
                        currentPathTextView.text = rightPath
                        storageInfoTextView.text = getString(R.string.storage_info_text, rightFolderCount, rightFileCount, getStorageInfo(rightPath))
                    }
                }
            }
        }
    }

    // 创建触摸监听器
    @SuppressLint("ClickableViewAccessibility")
    private fun createTouchListener(isLeft: Boolean): View.OnTouchListener {
        return View.OnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (isLeft) {
                    currentPathTextView.text = leftPath
                    storageInfoTextView.text = getString(R.string.storage_info_text, leftFolderCount, leftFileCount, getStorageInfo(leftPath))
                } else {
                    currentPathTextView.text = rightPath
                    storageInfoTextView.text = getString(R.string.storage_info_text, rightFolderCount, rightFileCount, getStorageInfo(rightPath))
                }
            }
            false
        }
    }

    // 处理文件项点击事件
    override fun onItemClick(file: File, isLeft: Boolean) {
        if (file.name == "..") {
            val currentDirectory = File(currentPathTextView.text.toString())
            val parentPath = currentDirectory.parent ?: return
            val files = getFilesFromDirectory(parentPath)
            val adapter = FileAdapter(addParentDirectory(files, parentPath), this, isLeft)

            if (isLeft) {
                val recyclerViewLeft: RecyclerView = findViewById(R.id.recyclerViewLeft)
                recyclerViewLeft.adapter = adapter
                leftPath = if (parentPath.endsWith("/")) parentPath else "$parentPath/"
                updateFolderAndFileCount(leftPath, true)
            } else {
                val recyclerViewRight: RecyclerView = findViewById(R.id.recyclerViewRight)
                recyclerViewRight.adapter = adapter
                rightPath = if (parentPath.endsWith("/")) parentPath else "$parentPath/"
                updateFolderAndFileCount(rightPath, false)
            }

            updateCurrentPath(parentPath)
            setStorageInfo(parentPath)
        } else if (file.isDirectory) {
            val files = getFilesFromDirectory(file.path)
            val adapter = FileAdapter(addParentDirectory(files, file.path), this, isLeft)

            if (isLeft) {
                val recyclerViewLeft: RecyclerView = findViewById(R.id.recyclerViewLeft)
                recyclerViewLeft.adapter = adapter
                leftPath = if (file.path.endsWith("/")) file.path else "${file.path}/"
                updateFolderAndFileCount(leftPath, true)
            } else {
                val recyclerViewRight: RecyclerView = findViewById(R.id.recyclerViewRight)
                recyclerViewRight.adapter = adapter
                rightPath = if (file.path.endsWith("/")) file.path else "${file.path}/"
                updateFolderAndFileCount(rightPath, false)
            }

            updateCurrentPath(file.path)
            setStorageInfo(file.path)
        }
    }

    // 处理文件项长按事件
    override fun onItemLongClick(file: File, isLeft: Boolean): Boolean {
        if (file.name != "..") {
            showFileOptionsDialog(file)
            return true
        }
        return false
    }

    // 显示文件选项对话框
    @SuppressLint("SetTextI18n")
    private fun showFileOptionsDialog(file: File) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_file_options)

        // 设置 Dialog 背景透明度（可选）
        dialog.window?.setDimAmount(0f)

        // 设置 Dialog 位置（向上偏移）
        dialog.window?.apply {
            val offsetY = (-7 * resources.displayMetrics.density).toInt()
            attributes = attributes?.apply {
                y = offsetY  // 负值表示向上偏移
            }
        }

        // 设置标题文本
        val AddTitle: TextView = dialog.findViewById(R.id.AddText)
        AddTitle.text = "<- ${getString(R.string.file_add)}"

        val MoveTitle: TextView = dialog.findViewById(R.id.MoveText)
        MoveTitle.text = "<- ${getString(R.string.file_move)}"

        dialog.window?.setWindowAnimations(R.style.DialogScaleAnimation)
        dialog.show()
    }

    // 获取目录中的文件列表
    private fun getFilesFromDirectory(path: String): List<File> {
        val directory = File(path)
        val files = directory.listFiles()?.toList() ?: emptyList()

        val directories = files.filter { it.isDirectory }.sortedBy { it.name }
        val regularFiles = files.filter { it.isFile }.sortedBy { it.name }

        return directories + regularFiles
    }

    // 添加父目录项
    private fun addParentDirectory(files: List<File>, currentPath: String): List<CustomFile> {
        val currentDirectory = File(currentPath)
        val parentDirectory = currentDirectory.parentFile
        return if (parentDirectory != null && currentPath != "/") {
            val parentFile = CustomFile(File(parentDirectory, ".."), true)
            listOf(parentFile) + files.map { CustomFile(it, false) }
        } else {
            files.map { CustomFile(it, false) }
        }
    }

    // 更新当前路径
    private fun updateCurrentPath(path: String) {
        val updatedPath = if (path.endsWith("/")) path else "$path/"
        currentPathTextView.text = updatedPath
    }

    // 设置存储信息
    private fun setStorageInfo(path: String) {
        val (folderCount, fileCount) = if (path == leftPath) {
            Pair(leftFolderCount, leftFileCount)
        } else {
            Pair(rightFolderCount, rightFileCount)
        }
        val storageInfo = getStorageInfo(path)
        storageInfoTextView.text = getString(R.string.storage_info_text, folderCount, fileCount, storageInfo)
    }

    // 更新文件夹和文件数量
    private fun updateFolderAndFileCount(path: String, isLeft: Boolean) {
        val (folderCount, fileCount) = getFolderAndFileCount(path)
        if (isLeft) {
            leftFolderCount = folderCount
            leftFileCount = fileCount
        } else {
            rightFolderCount = folderCount
            rightFileCount = fileCount
        }
    }

    // 获取文件夹和文件数量
    private fun getFolderAndFileCount(path: String): Pair<Int, Int> {
        val directory = File(path)
        var folderCount = 0
        var fileCount = 0
        directory.listFiles()?.forEach {
            if (it.isDirectory) {
                folderCount++
            } else {
                fileCount++
            }
        }
        return Pair(folderCount, fileCount)
    }

    // 获取存储信息
    @SuppressLint("DefaultLocale")
    private fun getStorageInfo(path: String): String {
        val stat = StatFs(path)
        val availableBytes = stat.availableBytes.toDouble()
        val totalBytes = stat.totalBytes.toDouble()
        val availableGB = availableBytes / (1024 * 1024 * 1024)
        val totalGB = totalBytes / (1024 * 1024 * 1024)
        return String.format("%.2fG/%.2fG", availableGB, totalGB)
    }
}