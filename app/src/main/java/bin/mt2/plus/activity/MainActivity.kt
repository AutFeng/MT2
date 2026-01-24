package bin.mt2.plus.activity

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
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import bin.mt2.plus.R
import bin.mt2.plus.adapter.FileAdapter
import bin.mt2.plus.callback.ItemTouchHelperCallback
import bin.mt2.plus.model.CustomFile
import bin.mt2.plus.utils.PathUtils
import bin.mt2.plus.widget.BezierGradientView
import bin.mt2.plus.widget.FastScrollRecyclerView
import bin.mt2.plus.widget.PullRefreshLayout
import bin.mt2.plus.widget.ScrollbarOverlayView
import com.google.android.material.navigation.NavigationView
import java.io.File

/**
 * 历史记录条目，包含路径和滚动位置
 */
data class HistoryEntry(
    val path: String,
    val scrollPosition: Int
)

class MainActivity : AppCompatActivity(), FileAdapter.OnItemClickListener {

    private var leftAdapter: FileAdapter? = null
    private var rightAdapter: FileAdapter? = null

    private lateinit var pullRefreshLeft: PullRefreshLayout
    private lateinit var pullRefreshRight: PullRefreshLayout
    private lateinit var currentPathTextView: TextView
    private lateinit var storageInfoTextView: TextView
    private lateinit var TextBar: LinearLayout
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var menuIcon: ImageView
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var leftPath: String
    private lateinit var rightPath: String

    private lateinit var leftTopBezierGradient: BezierGradientView
    private lateinit var leftBottomBezierGradient: BezierGradientView
    private lateinit var rightTopBezierGradient: BezierGradientView
    private lateinit var rightBottomBezierGradient: BezierGradientView
    private lateinit var leftRithtBezierGradient: BezierGradientView
    private lateinit var rightLeftBezierGradient: BezierGradientView
    private lateinit var toolbar: androidx.appcompat.widget.Toolbar
    private lateinit var bottomBar: LinearLayout
    private lateinit var leftScrollbarOverlay: ScrollbarOverlayView
    private lateinit var rightScrollbarOverlay: ScrollbarOverlayView
    private lateinit var arrowTeamIcon: ImageView
    private lateinit var addIcon: ImageView
    private lateinit var backIcon: ImageView
    private lateinit var forwardIcon: ImageView
    private lateinit var moreIcon: ImageView

    private var isInitialized = false

    // 跟踪当前选中的列表（true=左边，false=右边）
    private var isLeftSelected = true

    private var leftFolderCount = 0
    private var leftFileCount = 0
    private var rightFolderCount = 0
    private var rightFileCount = 0

    // 历史记录栈（左右列表独立，包含路径和滚动位置）
    private val leftHistoryStack = mutableListOf<HistoryEntry>()
    private val leftForwardStack = mutableListOf<HistoryEntry>()
    private val rightHistoryStack = mutableListOf<HistoryEntry>()
    private val rightForwardStack = mutableListOf<HistoryEntry>()

    private var permissionDialog: AlertDialog? = null

    @SuppressLint("MissingInflatedId", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 设置软键盘模式，防止键盘弹出时调整布局导致顶部文本被裁断
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

        // 初始化 Toolbar
        toolbar = findViewById(R.id.toolbar)
        bottomBar = findViewById(R.id.bottomBar)
        leftScrollbarOverlay = findViewById(R.id.leftScrollbarContainer)
        rightScrollbarOverlay = findViewById(R.id.rightScrollbarContainer)
        arrowTeamIcon = findViewById(R.id.arrowTeamIcon)
        addIcon = findViewById(R.id.addIcon)
        backIcon = findViewById(R.id.backIcon)
        forwardIcon = findViewById(R.id.forwardIcon)
        moreIcon = findViewById(R.id.moreIcon)

        // 设置后退按钮点击事件
        backIcon.setOnClickListener {
            navigateBack()
        }

        // 设置前进按钮点击事件
        forwardIcon.setOnClickListener {
            navigateForward()
        }

        // 设置更多按钮点击事件
        moreIcon.setOnClickListener {
            showMoreMenu()
        }

        // 设置图标点击事件：同步两个列表的路径
        arrowTeamIcon.setOnClickListener {
            if (isLeftSelected) {
                // 当前在左列表，同步右列表到左列表的路径
                rightPath = leftPath
                refreshList(false, rightPath)
                updateFolderAndFileCount(rightPath, false)
            } else {
                // 当前在右列表，同步左列表到右列表的路径
                leftPath = rightPath
                refreshList(true, leftPath)
                updateFolderAndFileCount(leftPath, true)
            }
        }

        // 设置添加按钮点击事件：显示编辑框弹窗
        addIcon.setOnClickListener {
            showAddDialog()
        }

        // 设置沉浸式状态栏和导航栏
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11 及以上
            window.setDecorFitsSystemWindows(false)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android 8.0 及以上
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            )
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Android 5.0 及以上
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }

        // 设置 Toolbar 的 padding 以适配状态栏
        toolbar.post {
            val statusBarHeight = getStatusBarHeight()
            toolbar.setPadding(0, statusBarHeight, 0, 0)
            val layoutParams = toolbar.layoutParams
            layoutParams.height = 56.dpToPx(this) + statusBarHeight
            toolbar.layoutParams = layoutParams
        }

        // 设置 bottomBar 的 padding 以适配导航栏
        bottomBar.post {
            val navigationBarHeight = getNavigationBarHeight()
            bottomBar.setPadding(0, 0, 0, navigationBarHeight)
        }

        // 初始化视图
        pullRefreshLeft = findViewById(R.id.pullRefreshLeft)
        pullRefreshRight = findViewById(R.id.pullRefreshRight)

        // 设置左右列表的移动方向
        pullRefreshLeft.setLeftToRight(true)   // 左列表：从左到右
        pullRefreshRight.setLeftToRight(false)  // 右列表：从右到左

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

        //  左右列表添加阴影事件
        val leftRecycle: FastScrollRecyclerView = findViewById(R.id.recyclerViewLeft)
        val rightRecycle: FastScrollRecyclerView = findViewById(R.id.recyclerViewRight)
        leftTopBezierGradient = findViewById(R.id.left_gradientTop)
        leftBottomBezierGradient = findViewById(R.id.left_gradientBottom)
        leftRithtBezierGradient = findViewById(R.id.left_gradientRight)
        rightTopBezierGradient = findViewById(R.id.right_gradientTop)
        rightBottomBezierGradient = findViewById(R.id.right_gradientBottom)
        rightLeftBezierGradient = findViewById(R.id.right_gradientLeft)

        // 默认右边阴影
        rightTopBezierGradient.visibility = View.VISIBLE
        rightBottomBezierGradient.visibility = View.VISIBLE
        rightLeftBezierGradient.visibility = View.VISIBLE
        leftTopBezierGradient.visibility = View.GONE
        leftBottomBezierGradient.visibility = View.GONE
        leftRithtBezierGradient.visibility = View.GONE

    }

    private fun determineIfLeft(): Boolean {
        return currentPathTextView.text.toString() == leftPath
    }

    // 显示更多菜单（从图标右上角展开并遮住图标）
    private fun showMoreMenu() {
        // 创建自定义布局
        val popupView = layoutInflater.inflate(R.layout.popup_more_menu, null)

        // 获取屏幕宽度
        val screenWidth = resources.displayMetrics.widthPixels

        // 设置菜单宽度为屏幕宽度的 47%
        val menuWidth = (screenWidth * 0.47).toInt()

        // 创建 PopupWindow，直接设置宽度
        val popupWindow = android.widget.PopupWindow(
            popupView,
            menuWidth,  // 直接设置宽度
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        // 设置背景（使用白色背景以支持 elevation 阴影）
        popupWindow.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE))

        // 设置自定义动画（从右上角展开和淡出）
        popupWindow.animationStyle = R.style.PopupMenuAnimation

        // 获取 Toolbar 位置（从 Toolbar 顶部开始展开）
        val location = IntArray(2)
        toolbar.getLocationOnScreen(location)
        val toolbarY = location[1]

        // 计算 PopupWindow 位置（右边缘紧贴屏幕右边缘，顶部对齐 Toolbar 顶部）
        val x = screenWidth - menuWidth
        val y = toolbarY

        // 设置菜单项点击事件
        popupView.findViewById<TextView>(R.id.menu_refresh).setOnClickListener {
            android.widget.Toast.makeText(this, "刷新功能待实现", android.widget.Toast.LENGTH_SHORT).show()
            popupWindow.dismiss()
        }

        popupView.findViewById<TextView>(R.id.menu_search).setOnClickListener {
            android.widget.Toast.makeText(this, "搜索功能待实现", android.widget.Toast.LENGTH_SHORT).show()
            popupWindow.dismiss()
        }

        popupView.findViewById<TextView>(R.id.menu_select_all).setOnClickListener {
            android.widget.Toast.makeText(this, "全选功能待实现", android.widget.Toast.LENGTH_SHORT).show()
            popupWindow.dismiss()
        }

        popupView.findViewById<TextView>(R.id.menu_filter).setOnClickListener {
            android.widget.Toast.makeText(this, "过滤功能待实现", android.widget.Toast.LENGTH_SHORT).show()
            popupWindow.dismiss()
        }

        popupView.findViewById<TextView>(R.id.menu_sort).setOnClickListener {
            android.widget.Toast.makeText(this, "排序方式功能待实现", android.widget.Toast.LENGTH_SHORT).show()
            popupWindow.dismiss()
        }

        popupView.findViewById<TextView>(R.id.menu_terminal).setOnClickListener {
            android.widget.Toast.makeText(this, "打开终端功能待实现", android.widget.Toast.LENGTH_SHORT).show()
            popupWindow.dismiss()
        }

        popupView.findViewById<TextView>(R.id.menu_hidden_files).setOnClickListener {
            android.widget.Toast.makeText(this, "隐藏文件功能待实现", android.widget.Toast.LENGTH_SHORT).show()
            popupWindow.dismiss()
        }

        popupView.findViewById<TextView>(R.id.menu_bookmark).setOnClickListener {
            android.widget.Toast.makeText(this, "添加书签功能待实现", android.widget.Toast.LENGTH_SHORT).show()
            popupWindow.dismiss()
        }

        popupView.findViewById<TextView>(R.id.menu_set_home).setOnClickListener {
            android.widget.Toast.makeText(this, "设为首页功能待实现", android.widget.Toast.LENGTH_SHORT).show()
            popupWindow.dismiss()
        }

        popupView.findViewById<TextView>(R.id.menu_settings).setOnClickListener {
            android.widget.Toast.makeText(this, "设置功能待实现", android.widget.Toast.LENGTH_SHORT).show()
            popupWindow.dismiss()
        }

        popupView.findViewById<TextView>(R.id.menu_exit).setOnClickListener {
            popupWindow.dismiss()
            finish()
        }

        // 显示 PopupWindow（使用屏幕坐标）
        popupWindow.showAtLocation(moreIcon, android.view.Gravity.NO_GRAVITY, x, y)
    }

    // 显示关于对话框
    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("关于")
            .setMessage("MT2 文件管理器\n版本：1.0")
            .setPositiveButton("确定", null)
            .show()
    }

    // 导航到指定路径（支持零宽字符绕过限制）
    private fun navigateToPath(path: String) {
        val isLeft = determineIfLeft()


        if (isLeft) {
            leftPath = path
            refreshList(true, path)
        } else {
            rightPath = path
            refreshList(false, path)
        }
    }

    // 后退导航
    private fun navigateBack() {
        val isLeft = isLeftSelected
        val historyStack = if (isLeft) leftHistoryStack else rightHistoryStack
        val forwardStack = if (isLeft) leftForwardStack else rightForwardStack
        val currentPath = if (isLeft) leftPath else rightPath

        if (historyStack.isEmpty()) {
            return // 没有历史记录，不执行操作
        }

        // 获取当前滚动位置
        val recyclerView: FastScrollRecyclerView =
            if (isLeft) findViewById(R.id.recyclerViewLeft)
            else findViewById(R.id.recyclerViewRight)
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
        val currentScrollPosition = layoutManager?.findFirstVisibleItemPosition() ?: 0

        // 将当前路径和滚动位置压入前进栈
        forwardStack.add(HistoryEntry(currentPath, currentScrollPosition))

        // 从历史栈弹出上一个条目
        val previousEntry = historyStack.removeAt(historyStack.size - 1)

        // 保存当前路径，用于高亮显示
        val highlightPath = currentPath.trimEnd('/')

        // 导航到上一个路径（不添加到历史记录，但高亮返回的文件夹）
        if (isLeft) {
            leftPath = previousEntry.path
            refreshList(true, previousEntry.path, addToHistory = false, highlightPath = highlightPath)
        } else {
            rightPath = previousEntry.path
            refreshList(false, previousEntry.path, addToHistory = false, highlightPath = highlightPath)
        }

        // 恢复滚动位置
        layoutManager?.scrollToPositionWithOffset(previousEntry.scrollPosition, 0)

        // 更新箭头状态
        updateNavigationIcons()
    }

    // 前进导航
    private fun navigateForward() {
        val isLeft = isLeftSelected
        val historyStack = if (isLeft) leftHistoryStack else rightHistoryStack
        val forwardStack = if (isLeft) leftForwardStack else rightForwardStack
        val currentPath = if (isLeft) leftPath else rightPath

        if (forwardStack.isEmpty()) {
            return // 没有前进记录，不执行操作
        }

        // 获取当前滚动位置
        val recyclerView: FastScrollRecyclerView =
            if (isLeft) findViewById(R.id.recyclerViewLeft)
            else findViewById(R.id.recyclerViewRight)
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
        val currentScrollPosition = layoutManager?.findFirstVisibleItemPosition() ?: 0

        // 将当前路径和滚动位置压入历史栈
        historyStack.add(HistoryEntry(currentPath, currentScrollPosition))

        // 从前进栈弹出下一个条目
        val nextEntry = forwardStack.removeAt(forwardStack.size - 1)

        // 保存当前路径，用于高亮显示
        val highlightPath = currentPath.trimEnd('/')

        // 导航到下一个路径（不添加到历史记录，但高亮返回的文件夹）
        if (isLeft) {
            leftPath = nextEntry.path
            refreshList(true, nextEntry.path, addToHistory = false, highlightPath = highlightPath)
        } else {
            rightPath = nextEntry.path
            refreshList(false, nextEntry.path, addToHistory = false, highlightPath = highlightPath)
        }

        // 恢复滚动位置
        layoutManager?.scrollToPositionWithOffset(nextEntry.scrollPosition, 0)

        // 更新箭头状态
        updateNavigationIcons()
    }

    // 更新导航图标状态（根据历史记录栈状态）
    private fun updateNavigationIcons() {
        val isLeft = isLeftSelected
        val historyStack = if (isLeft) leftHistoryStack else rightHistoryStack
        val forwardStack = if (isLeft) leftForwardStack else rightForwardStack

        // 更新后退按钮：有历史记录时亮起（alpha=1.0），否则半透明（alpha=0.5）
        backIcon.alpha = if (historyStack.isNotEmpty()) 1.0f else 0.5f

        // 更新前进按钮：有前进记录时亮起（alpha=1.0），否则半透明（alpha=0.5）
        forwardIcon.alpha = if (forwardStack.isNotEmpty()) 1.0f else 0.5f
    }

    // 更新当前路径显示（不再手动截断，依赖 XML 的 ellipsize）
    private fun truncatePath(path: String): String {
        return path
    }

    // 更新当前路径显示
    private fun updateCurrentPath(path: String) {
        val updatedPath = if (path.endsWith("/")) path else "$path/"
        currentPathTextView.text = updatedPath
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

    private fun refreshList(isLeft: Boolean, newPath: String, addToHistory: Boolean = true, highlightPath: String? = null) {
        val recyclerView: FastScrollRecyclerView =
            if (isLeft) findViewById(R.id.recyclerViewLeft)
            else findViewById(R.id.recyclerViewRight)

        val files = getFilesFromDirectory(newPath)
        val newFileList = addParentDirectory(files, newPath)

        //  重用 Adapter，只更新数据
        val adapter = if (isLeft) leftAdapter else rightAdapter

        if (adapter != null) {
            adapter.updateData(newFileList)
        } else {
            // 首次创建
            val newAdapter = FileAdapter(newFileList, this, isLeft)
            recyclerView.adapter = newAdapter
            if (isLeft) leftAdapter = newAdapter else rightAdapter = newAdapter
        }

        updateFolderAndFileCount(newPath, isLeft)
        updateCurrentPath(newPath)
        setStorageInfo(newPath)

        // 如果需要高亮某个路径（触发水波纹效果）
        if (highlightPath != null) {
            recyclerView.post {
                val position = newFileList.indexOfFirst { it.file.absolutePath == highlightPath }
                if (position != -1) {
                    val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
                    layoutManager?.findViewByPosition(position)?.let { itemView ->
                        // 触发水波纹效果
                        itemView.isPressed = true
                        itemView.postDelayed({
                            itemView.isPressed = false
                        }, 300)
                    }
                }
            }
        }

        // 更新导航图标状态
        updateNavigationIcons()
    }

    // 扩展函数：dp转px
    private fun Int.dpToPx(context: Context): Int = (this * context.resources.displayMetrics.density).toInt()

    // 获取状态栏高度
    private fun getStatusBarHeight(): Int {
        var result = 0
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

    // 获取导航栏高度
    private fun getNavigationBarHeight(): Int {
        var result = 0
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

    // 带渐变效果的图标切换方法（交叉渐变融合）
    private fun updateArrowTeamIcon(isLeft: Boolean) {
        // 更新当前选中状态
        isLeftSelected = isLeft

        val newDrawableRes = if (isLeft) {
            R.drawable.baseline_arrow_team_24_left
        } else {
            R.drawable.baseline_arrow_team_24_right
        }

        // 创建一个临时ImageView来显示旧图标
        val tempImageView = ImageView(this).apply {
            setImageDrawable(arrowTeamIcon.drawable)
            imageTintList = arrowTeamIcon.imageTintList
            scaleType = arrowTeamIcon.scaleType
        }

        // 获取原ImageView在屏幕上的位置
        val location = IntArray(2)
        arrowTeamIcon.getLocationInWindow(location)

        // 将临时ImageView添加到根布局，使用绝对定位
        val rootLayout = findViewById<ViewGroup>(android.R.id.content)
        val params = ViewGroup.LayoutParams(
            arrowTeamIcon.width,
            arrowTeamIcon.height
        )
        tempImageView.layoutParams = params
        tempImageView.x = location[0].toFloat()
        tempImageView.y = location[1].toFloat()
        rootLayout.addView(tempImageView)

        // 立即切换原ImageView的图标并设置为透明
        arrowTeamIcon.setImageResource(newDrawableRes)
        arrowTeamIcon.alpha = 0f

        // 同时执行两个动画：旧图标淡出，新图标淡入
        tempImageView.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                // 动画结束后移除临时ImageView
                rootLayout.removeView(tempImageView)
            }
            .start()

        arrowTeamIcon.animate()
            .alpha(1f)
            .setDuration(200)
            .start()
    }

    // 显示添加弹窗（创建文件夹或文件）
    private fun showAddDialog() {
        val currentPath = if (isLeftSelected) leftPath else rightPath

        // 创建容器并设置padding
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(this@MainActivity), 0, 24.dpToPx(this@MainActivity), 0)
        }

        // 创建编辑框
        val editText = EditText(this).apply {
            setSingleLine(true)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            highlightColor = ContextCompat.getColor(this@MainActivity, R.color.DialogSelectedColor)
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.DialogButtonColor))
            // 设置光标颜色
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                textCursorDrawable = ContextCompat.getDrawable(this@MainActivity, R.drawable.cursor_color)
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

        // 创建弹窗
        val dialog = AlertDialog.Builder(this)
            .setTitle("创建")
            .setView(container)
            .setPositiveButton("文件夹") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    createFolder(name, currentPath)
                }
            }
            .setNegativeButton("文件") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    createFile(name, currentPath)
                }
            }
            .setNeutralButton("取消", null)
            .create()

        // 设置按钮文字颜色
        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            val neutralButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            positiveButton.setTextColor(ContextCompat.getColor(this, R.color.DialogButtonColor))
            negativeButton.setTextColor(ContextCompat.getColor(this, R.color.DialogButtonColor))
            neutralButton.setTextColor(ContextCompat.getColor(this, R.color.DialogButtonColor))

            // 显示键盘并获取焦点
            editText.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            Handler().postDelayed({
                imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
            }, 100)
        }

        // 设置对话框的软键盘模式，防止键盘弹出时调整主窗口布局
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

        // 显示弹窗
        dialog.show()
    }

    // 创建文件夹
    private fun createFolder(name: String, parentPath: String) {
        val newFolder = File(parentPath, name)
        if (newFolder.exists()) {
            android.widget.Toast.makeText(this, "文件夹已存在", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        if (newFolder.mkdir()) {
            android.widget.Toast.makeText(this, "文件夹创建成功", android.widget.Toast.LENGTH_SHORT).show()
            // 刷新当前列表并定位到新创建的项
            refreshListAndScrollToNew(isLeftSelected, parentPath, newFolder.name)
        } else {
            android.widget.Toast.makeText(this, "文件夹创建失败", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // 创建文件
    private fun createFile(name: String, parentPath: String) {
        val newFile = File(parentPath, name)
        if (newFile.exists()) {
            android.widget.Toast.makeText(this, "文件已存在", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        try {
            if (newFile.createNewFile()) {
                android.widget.Toast.makeText(this, "文件创建成功", android.widget.Toast.LENGTH_SHORT).show()
                // 刷新当前列表并定位到新创建的项
                refreshListAndScrollToNew(isLeftSelected, parentPath, newFile.name)
            } else {
                android.widget.Toast.makeText(this, "文件创建失败", android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "创建失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // 刷新列表并滚动到新创建的项
    private fun refreshListAndScrollToNew(isLeft: Boolean, path: String, newFileName: String) {
        val recyclerView: FastScrollRecyclerView =
            if (isLeft) findViewById(R.id.recyclerViewLeft)
            else findViewById(R.id.recyclerViewRight)

        val files = getFilesFromDirectory(path)
        val fileListWithParent = addParentDirectory(files, path)

        // 标记新创建的文件
        val updatedList = fileListWithParent.map { customFile ->
            if (customFile.file.name == newFileName) {
                CustomFile(customFile.file, customFile.isParent, true)
            } else {
                customFile
            }
        }

        // 更新Adapter
        val adapter = if (isLeft) leftAdapter else rightAdapter
        adapter?.updateData(updatedList)

        // 更新TextBar显示
        currentPathTextView.text = truncatePath(path)
        setStorageInfo(path)

        // 更新文件夹和文件计数
        updateFolderAndFileCount(path, isLeft)

        // 找到新创建项的位置并立即跳转
        val newItemPosition = updatedList.indexOfFirst { it.file.name == newFileName }
        if (newItemPosition != -1) {
            val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
            layoutManager?.scrollToPositionWithOffset(newItemPosition, 0)
        }
    }

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

    @SuppressLint("ClickableViewAccessibility")
    private fun initializeFileLists() {
        val recyclerViewLeft: FastScrollRecyclerView = findViewById(R.id.recyclerViewLeft)
        recyclerViewLeft.layoutManager = LinearLayoutManager(this)
        val filesLeft = getFilesFromDirectory("/storage/emulated/0/")
        leftAdapter = FileAdapter(addParentDirectory(filesLeft, "/storage/emulated/0/"), this, true)
        recyclerViewLeft.adapter = leftAdapter
        leftPath = "/storage/emulated/0/"
        updateFolderAndFileCount(leftPath, true)
        
        // 连接左侧RecyclerView和滑块覆盖层
        recyclerViewLeft.setScrollbarStateListener(object : FastScrollRecyclerView.ScrollbarStateListener {
            override fun onScrollbarStateChanged(rect: android.graphics.RectF, visible: Boolean, dragging: Boolean, alpha: Float) {
                leftScrollbarOverlay.updateScrollbar(rect, visible, dragging, alpha)
            }
        })

        val recyclerViewRight: FastScrollRecyclerView = findViewById(R.id.recyclerViewRight)
        recyclerViewRight.layoutManager = LinearLayoutManager(this)
        val filesRight = getFilesFromDirectory("/storage/emulated/0/")
        rightAdapter = FileAdapter(addParentDirectory(filesRight, "/storage/emulated/0/"), this, false)
        recyclerViewRight.adapter = rightAdapter
        rightPath = "/storage/emulated/0/"
        updateFolderAndFileCount(rightPath, false)
        
        // 连接右侧RecyclerView和滑块覆盖层
        recyclerViewRight.setScrollbarStateListener(object : FastScrollRecyclerView.ScrollbarStateListener {
            override fun onScrollbarStateChanged(rect: android.graphics.RectF, visible: Boolean, dragging: Boolean, alpha: Float) {
                rightScrollbarOverlay.updateScrollbar(rect, visible, dragging, alpha)
            }
        })

        // 添加左右滑动支持
        val leftTouchHelper = ItemTouchHelper(ItemTouchHelperCallback { position ->
            leftAdapter?.selectIfNotSelected(position)
        })
        leftTouchHelper.attachToRecyclerView(recyclerViewLeft)

        val rightTouchHelper = ItemTouchHelper(ItemTouchHelperCallback { position ->
            rightAdapter?.selectIfNotSelected(position)
        })
        rightTouchHelper.attachToRecyclerView(recyclerViewRight)

        recyclerViewLeft.addOnScrollListener(createScrollListener(true))
        recyclerViewRight.addOnScrollListener(createScrollListener(false))
        recyclerViewLeft.setOnTouchListener(createTouchListener(true))
        recyclerViewRight.setOnTouchListener(createTouchListener(false))

        // 设置下拉开始监听器
        pullRefreshLeft.onPullStartListener = {
            currentPathTextView.text = truncatePath(leftPath)
            setStorageInfo(leftPath)
            // 更新图标为左边选中状态（带渐变效果）
            updateArrowTeamIcon(true)
        }

        pullRefreshRight.onPullStartListener = {
            currentPathTextView.text = truncatePath(rightPath)
            setStorageInfo(rightPath)
            // 更新图标为右边选中状态（带渐变效果）
            updateArrowTeamIcon(false)
        }

        // 设置下拉刷新监听器
        pullRefreshLeft.setOnRefreshListener {
            Handler().postDelayed({
                refreshList(true, leftPath)
                pullRefreshLeft.finishRefresh()
            }, 0)
        }

        pullRefreshRight.setOnRefreshListener {
            Handler().postDelayed({
                refreshList(false, rightPath)
                pullRefreshRight.finishRefresh()
            }, 0)
        }

        updateCurrentPath("/storage/emulated/0/")
        setStorageInfo("/storage/emulated/0/")

        isInitialized = true

        // 初始化导航图标状态（历史记录栈为空，所以两个箭头都应该是半透明）
        updateNavigationIcons()

        pullRefreshLeft.setOnTouchListener { _, _ ->

            // 默认右边阴影
            rightTopBezierGradient.visibility = View.VISIBLE
            rightBottomBezierGradient.visibility = View.VISIBLE
            rightLeftBezierGradient.visibility = View.VISIBLE
            leftTopBezierGradient.visibility = View.GONE
            leftBottomBezierGradient.visibility = View.GONE
            leftRithtBezierGradient.visibility = View.GONE

            false
        }

        pullRefreshRight.setOnTouchListener { _, _ ->

            leftTopBezierGradient.visibility = View.VISIBLE
            leftBottomBezierGradient.visibility = View.VISIBLE
            leftRithtBezierGradient.visibility = View.VISIBLE
            rightTopBezierGradient.visibility = View.GONE
            rightBottomBezierGradient.visibility = View.GONE
            rightLeftBezierGradient.visibility = View.GONE

            false
        }

    }

    // 在滚动监听器中修改
    private fun createScrollListener(isLeft: Boolean): RecyclerView.OnScrollListener {
        return object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy != 0) {
                    if (isLeft) {
                        currentPathTextView.text = truncatePath(leftPath)
                        setStorageInfo(leftPath)
                        // 更新图标为左边选中状态（带渐变效果）
                        updateArrowTeamIcon(true)
                    } else {
                        currentPathTextView.text = truncatePath(rightPath)
                        setStorageInfo(rightPath)
                        // 更新图标为右边选中状态（带渐变效果）
                        updateArrowTeamIcon(false)
                    }
                }

            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (isLeft) {
                    rightTopBezierGradient.visibility = View.VISIBLE
                    rightBottomBezierGradient.visibility = View.VISIBLE
                    rightLeftBezierGradient.visibility = View.VISIBLE
                    leftTopBezierGradient.visibility = View.GONE
                    leftBottomBezierGradient.visibility = View.GONE
                    leftRithtBezierGradient.visibility = View.GONE
                } else {
                    leftTopBezierGradient.visibility = View.VISIBLE
                    leftBottomBezierGradient.visibility = View.VISIBLE
                    leftRithtBezierGradient.visibility = View.VISIBLE
                    rightTopBezierGradient.visibility = View.GONE
                    rightBottomBezierGradient.visibility = View.GONE
                    rightLeftBezierGradient.visibility = View.GONE
                }
            }

        }
    }

    // 创建触摸监听器
    @SuppressLint("ClickableViewAccessibility")
    private fun createTouchListener(isLeft: Boolean): View.OnTouchListener {
        return View.OnTouchListener { _, event ->

            if (isLeft) {
                rightTopBezierGradient.visibility = View.VISIBLE
                rightBottomBezierGradient.visibility = View.VISIBLE
                rightLeftBezierGradient.visibility = View.VISIBLE
                leftTopBezierGradient.visibility = View.GONE
                leftBottomBezierGradient.visibility = View.GONE
                leftRithtBezierGradient.visibility = View.GONE
                // 切换到左边选中的图标（带渐变效果）
                updateArrowTeamIcon(true)
            } else {
                leftTopBezierGradient.visibility = View.VISIBLE
                leftBottomBezierGradient.visibility = View.VISIBLE
                leftRithtBezierGradient.visibility = View.VISIBLE
                rightTopBezierGradient.visibility = View.GONE
                rightBottomBezierGradient.visibility = View.GONE
                rightLeftBezierGradient.visibility = View.GONE
                // 切换到右边选中的图标（带渐变效果）
                updateArrowTeamIcon(false)
            }

            if (event.action == MotionEvent.ACTION_DOWN) {

                if (isLeft) {
                    currentPathTextView.text = truncatePath(leftPath)
                    setStorageInfo(leftPath)
                } else {
                    currentPathTextView.text = truncatePath(rightPath)
                    setStorageInfo(rightPath)
                }

            }
            false
        }
    }

    // 处理文件项点击事件
    override fun onItemClick(file: File, isLeft: Boolean) {
        if (isLeft) {
            currentPathTextView.text = truncatePath(leftPath)
            setStorageInfo(leftPath)
            // 更新图标为左边选中状态（带渐变效果）
            updateArrowTeamIcon(true)
        } else {
            currentPathTextView.text = truncatePath(rightPath)
            setStorageInfo(rightPath)
            // 更新图标为右边选中状态（带渐变效果）
            updateArrowTeamIcon(false)
        }

        if (file.name == "..") {
            val currentPath = if (isLeft) leftPath else rightPath
            val currentDirectory = File(currentPath)
            val parentPath = currentDirectory.parent ?: return
            val historyStack = if (isLeft) leftHistoryStack else rightHistoryStack

            // 检查历史记录中最后一个条目是否是父目录
            val lastEntry = historyStack.lastOrNull()
            if (lastEntry != null && lastEntry.path == (if (parentPath.endsWith("/")) parentPath else "$parentPath/")) {
                // 如果历史记录中有父目录，使用后退功能恢复位置
                navigateBack()
            } else {
                // 否则正常返回父目录（从顶部开始）
                val forwardStack = if (isLeft) leftForwardStack else rightForwardStack

                // 获取当前滚动位置
                val recyclerView: FastScrollRecyclerView =
                    if (isLeft) findViewById(R.id.recyclerViewLeft)
                    else findViewById(R.id.recyclerViewRight)
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
                val currentScrollPosition = layoutManager?.findFirstVisibleItemPosition() ?: 0

                // 添加当前路径和滚动位置到历史记录
                historyStack.add(HistoryEntry(currentPath, currentScrollPosition))
                // 清空前进栈（因为进入了新路径）
                forwardStack.clear()

                // 返回父目录
                if (isLeft) {
                    leftPath = if (parentPath.endsWith("/")) parentPath else "$parentPath/"
                    refreshList(true, leftPath, addToHistory = false)
                } else {
                    rightPath = if (parentPath.endsWith("/")) parentPath else "$parentPath/"
                    refreshList(false, rightPath, addToHistory = false)
                }
            }
        } else if (file.isDirectory) {
            // 点击文件夹进入，添加当前路径和滚动位置到历史记录
            val currentPath = if (isLeft) leftPath else rightPath
            val historyStack = if (isLeft) leftHistoryStack else rightHistoryStack
            val forwardStack = if (isLeft) leftForwardStack else rightForwardStack

            // 获取当前滚动位置
            val recyclerView: FastScrollRecyclerView =
                if (isLeft) findViewById(R.id.recyclerViewLeft)
                else findViewById(R.id.recyclerViewRight)
            val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
            val currentScrollPosition = layoutManager?.findFirstVisibleItemPosition() ?: 0

            // 添加当前路径和滚动位置到历史记录
            historyStack.add(HistoryEntry(currentPath, currentScrollPosition))
            // 清空前进栈（因为进入了新路径）
            forwardStack.clear()

            // 进入新文件夹
            if (isLeft) {
                leftPath = if (file.path.endsWith("/")) file.path else "${file.path}/"
                refreshList(true, leftPath, addToHistory = false)
            } else {
                rightPath = if (file.path.endsWith("/")) file.path else "${file.path}/"
                refreshList(false, rightPath, addToHistory = false)
            }
        }
        if (isLeft) {
            rightTopBezierGradient.visibility = View.VISIBLE
            rightBottomBezierGradient.visibility = View.VISIBLE
            rightLeftBezierGradient.visibility = View.VISIBLE
            leftTopBezierGradient.visibility = View.GONE
            leftBottomBezierGradient.visibility = View.GONE
            leftRithtBezierGradient.visibility = View.GONE
        } else {
            leftTopBezierGradient.visibility = View.VISIBLE
            leftBottomBezierGradient.visibility = View.VISIBLE
            leftRithtBezierGradient.visibility = View.VISIBLE
            rightTopBezierGradient.visibility = View.GONE
            rightBottomBezierGradient.visibility = View.GONE
            rightLeftBezierGradient.visibility = View.GONE
        }

    }

    // 处理文件项长按事件
    override fun onItemLongClick(file: File, isLeft: Boolean): Boolean {
        if (file.name != "..") {
            showFileOptionsDialog()
            return true
        }
        return false
    }

    // 显示文件选项对话框
    @SuppressLint("SetTextI18n")
    private fun showFileOptionsDialog() {
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

    // 获取目录中的文件列表（支持零宽字符绕过限制）
    private fun getFilesFromDirectory(path: String): List<File> {
        // 使用PathUtils处理路径，自动添加零宽字符绕过限制
        val cleanPath = PathUtils.removeZeroWidthChar(path)
        val directory = PathUtils.getFile(cleanPath)
        val files = directory.listFiles()?.toList() ?: emptyList()

        val directories = files.filter { it.isDirectory }.sortedBy { it.name }
        val regularFiles = files.filter { it.isFile }.sortedBy { it.name }

        return directories + regularFiles
    }

    // 添加父目录项（支持零宽字符路径）
    private fun addParentDirectory(files: List<File>, currentPath: String): List<CustomFile> {
        val cleanPath = PathUtils.removeZeroWidthChar(currentPath)
        val currentDirectory = PathUtils.getFile(cleanPath)
        val parentDirectory = currentDirectory.parentFile
        return if (parentDirectory != null && cleanPath != "/") {
            val parentFile = CustomFile(File(parentDirectory, ".."), true)
            listOf(parentFile) + files.map { CustomFile(it, false) }
        } else {
            files.map { CustomFile(it, false) }
        }
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

    // 获取文件夹和文件数量（支持零宽字符路径）
    private fun getFolderAndFileCount(path: String): Pair<Int, Int> {
        val cleanPath = PathUtils.removeZeroWidthChar(path)
        val directory = PathUtils.getFile(cleanPath)
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

    // 获取存储信息（支持零宽字符路径）
    @SuppressLint("DefaultLocale")
    private fun getStorageInfo(path: String): String {
        return try {
            // 清理路径中的零宽字符
            val cleanPath = PathUtils.removeZeroWidthChar(path)
            
            // 对于Android/data和Android/obb目录，使用其父目录来获取存储信息
            val statPath = if (cleanPath.contains("/Android/data") || cleanPath.contains("/Android/obb")) {
                // 使用存储根目录
                "/storage/emulated/0"
            } else {
                cleanPath
            }
            
            val stat = StatFs(statPath)
            val availableBytes = stat.availableBytes.toDouble()
            val totalBytes = stat.totalBytes.toDouble()
            val availableGB = availableBytes / (1024 * 1024 * 1024)
            val totalGB = totalBytes / (1024 * 1024 * 1024)
            String.format("%.2fG/%.2fG", availableGB, totalGB)
        } catch (e: Exception) {
            // 如果获取失败，返回默认值
            "N/A"
        }
    }
}