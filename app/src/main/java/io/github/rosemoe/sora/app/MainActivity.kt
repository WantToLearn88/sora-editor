package io.github.rosemoe.sora.app

import android.content.Context
import android.content.DialogInterface
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.github.rosemoe.sora.app.databinding.ActivityMainBinding
import io.github.rosemoe.sora.app.fragment.EditorFragment
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.EditorKeyEvent
import io.github.rosemoe.sora.event.InlayHintClickEvent
import io.github.rosemoe.sora.event.KeyBindingEvent
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.event.SideIconClickEvent
import io.github.rosemoe.sora.event.TextSizeChangeEvent
import io.github.rosemoe.sora.langs.monarch.MonarchColorScheme
import io.github.rosemoe.sora.langs.monarch.MonarchLanguage
import io.github.rosemoe.sora.langs.monarch.registry.MonarchGrammarRegistry
import io.github.rosemoe.sora.langs.monarch.registry.dsl.monarchLanguages
import io.github.rosemoe.sora.langs.monarch.registry.model.ThemeSource
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.dsl.languages
import io.github.rosemoe.sora.langs.textmate.registry.model.DefaultGrammarDefinition
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.text.ContentIO
import io.github.rosemoe.sora.text.LineSeparator
import io.github.rosemoe.sora.utils.CrashHandler
import io.github.rosemoe.sora.utils.codePointStringAt
import io.github.rosemoe.sora.utils.escapeCodePointIfNecessary
import io.github.rosemoe.sora.utils.toast
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.EditorSearcher.SearchOptions
import io.github.rosemoe.sora.widget.SelectionMovement
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.component.Magnifier
import io.github.rosemoe.sora.widget.ext.EditorSpanInteractionHandler
import io.github.rosemoe.sora.widget.getComponent
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula
import io.github.rosemoe.sora.widget.schemes.SchemeEclipse
import io.github.rosemoe.sora.widget.schemes.SchemeGitHub
import io.github.rosemoe.sora.widget.schemes.SchemeNotepadXX
import io.github.rosemoe.sora.widget.schemes.SchemeVS2019
import io.github.rosemoe.sora.widget.style.LineInfoPanelPosition
import io.github.rosemoe.sora.widget.style.LineInfoPanelPositionMode
import io.github.rosemoe.sora.widget.subscribeAlways
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.tm4e.core.registry.IGrammarSource
import org.eclipse.tm4e.core.registry.IThemeSource
import java.util.regex.PatternSyntaxException


/**
 * Demo and debug Activity for the code editor
 */
class MainActivity : AppCompatActivity() {

    companion object {
        init {
            // Load tree-sitter libraries
            System.loadLibrary("android-tree-sitter")
            System.loadLibrary("tree-sitter-java")
            AsyncIncrementalAnalyzeManager.setUseShallowCopyByDefault(true)
        }

        private const val TAG = "MainActivity"
        const val LOG_FILE = "crash-journal.log"

        /**
         * Symbols to be displayed in symbol input view
         */
        val SYMBOLS = arrayOf(
            "->", "{", "}", "(", ")",
            ",", ".", ";", "\"", "?",
            "+", "-", "*", "/", "<",
            ">", "[", "]", ":"
        )

        /**
         * Texts to be committed to editor for symbols above
         */
        val SYMBOL_INSERT_TEXT = arrayOf(
            "\t", "{}", "}", "(", ")",
            ",", ".", ";", "\"", "?",
            "+", "-", "*", "/", "<",
            ">", "[", "]", ":"
        )

    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var searchMenu: PopupMenu
    private var searchOptions = SearchOptions(false, false)
    private var undo: MenuItem? = null
    private var redo: MenuItem? = null

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var tabAdapter: TabAdapter
    private val tabs = mutableListOf<TabInfo>()
    private var currentTabId = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashHandler.INSTANCE.init(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.activityToolbar)
        applyEdgeToEdge(this, binding.toolbarContainer, binding.root)

        val typeface = Typeface.createFromAsset(assets, "JetBrainsMono-Regular.ttf")

        // Initialize ViewPager and TabLayout
        viewPager = binding.viewPager
        tabLayout = binding.tabLayout
        tabAdapter = TabAdapter(this, tabs)
        viewPager.adapter = tabAdapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = tabs[position].title
            val closeButton = View.inflate(this, R.layout.tab_close_button, null)
            tab.customView = closeButton
            closeButton.findViewById<View>(R.id.tab_close_icon).setOnClickListener { // Assuming you have an ImageView with id tab_close_icon in tab_close_button.xml
                showDeleteTabDialog(position)
            }
            tab.view.setOnLongClickListener { // Long press to rename
                showRenameTabDialog(position)
                true
            }
        }.attach()

        // Load saved tabs or create a default one
        loadTabs()
        if (tabs.isEmpty()) {
            addTab("Untitled 1", "")
        }

        // Add new tab button
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (tab?.position == tabLayout.tabCount - 1 && tab.text == "+") {
                    addTab("Untitled ${tabs.size + 1}", "")
                    tabLayout.removeTabAt(tabLayout.tabCount - 1)
                    tabLayout.addTab(tabLayout.newTab().setText("+"), false)
                    viewPager.currentItem = tabs.size - 1
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        tabLayout.addTab(tabLayout.newTab().setText("+"), false)

        // Setup Listeners
        binding.apply {
            btnGotoPrev.setOnClickListener(::gotoPrev)
            btnGotoNext.setOnClickListener(::gotoNext)
            btnReplace.setOnClickListener(::replace)
            btnReplaceAll.setOnClickListener(::replaceAll)
            searchOptions.setOnClickListener(::showSearchOptions)
        }

        // Configure symbol input view
        val inputView = binding.symbolInput
        inputView.bindEditor(getCurrentEditor())
        inputView.addSymbols(SYMBOLS, SYMBOL_INSERT_TEXT)
        inputView.forEachButton { it.typeface = typeface }

        // Commit search when text changed
        binding.searchEditor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun afterTextChanged(editable: Editable) {
                tryCommitSearch()
            }
        })

        // Search options
        searchMenu = PopupMenu(this, binding.searchOptions)
        searchMenu.inflate(R.menu.menu_search_options)
        searchMenu.setOnMenuItemClickListener {
            // Update option states
            it.isChecked = !it.isChecked
            if (it.isChecked) {
                // Regex and whole word mode can not be both chose
                when (it.itemId) {
                    R.id.search_option_regex -> {
                        searchMenu.menu.findItem(R.id.search_option_whole_word)!!.isChecked = false
                    }

                    R.id.search_option_whole_word -> {
                        searchMenu.menu.findItem(R.id.search_option_regex)!!.isChecked = false
                    }
                }
            }
            // Update search options and commit search with the new options
            computeSearchOptions()
            tryCommitSearch()
            true
        }

        // Configure editor
        getCurrentEditor().apply {
            registerInlayHintRenderers(
                TextInlayHintRenderer.DefaultInstance,
                ColorInlayHintRenderer.DefaultInstance
            )
            typefaceText = typeface
            props.stickyScroll = true
            setLineSpacing(2f, 1.1f)
            nonPrintablePaintingFlags =
                CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or CodeEditor.FLAG_DRAW_LINE_SEPARATOR or CodeEditor.FLAG_DRAW_WHITESPACE_IN_SELECTION or CodeEditor.FLAG_DRAW_SOFT_WRAP
            // Update the editor's language and color scheme based on file extension
            // This logic will need to be updated to handle different languages per tab
            // For now, we'll keep the existing logic for the initial tab
            // setEditorLanguage(this, "java") // Example, will be dynamic
            // setColorScheme(SchemeDarcula()) // Example, will be dynamic

            subscribeAlways<ContentChangeEvent>(this) { _, _ ->
                // Save content of the current tab when it changes
                val currentFragment = tabAdapter.getFragment(viewPager.currentItem) as? EditorFragment
                currentFragment?.let { fragment ->
                    val tabInfo = tabs[viewPager.currentItem]
                    tabInfo.content = fragment.editor.text.toString()
                    saveTabs()
                }
            }

            subscribeAlways<SelectionChangeEvent>(this) { _, _ ->
                updatePositionText()
            }

            subscribeAlways<TextSizeChangeEvent>(this) { _, _ ->
                updatePositionText()
            }

            subscribeAlways<EditorKeyEvent>(this) { _, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (event.keyCode == KeyEvent.KEYCODE_TAB) {
                        if (event.is and KeyEvent.META_SHIFT_ON) {
                            moveSelection(SelectionMovement.MOVE_WORD_LEFT)
                        } else {
                            moveSelection(SelectionMovement.MOVE_WORD_RIGHT)
                        }
                        return@subscribeAlways
                    }
                }
                if (event.keyCode == KeyEvent.KEYCODE_MENU) {
                    return@subscribeAlways
                }
                if (event.action == KeyEvent.ACTION_UP) {
                    if (event.keyCode == KeyEvent.KEYCODE_MENU) {
                        openOptionsMenu()
                        return@subscribeAlways
                    }
                }
            }

            subscribeAlways<KeyBindingEvent>(this) { _, event ->
                if (event.eventType == KeyBindingEvent.EventType.KEY_DOWN) {
                    if (event.key == KeyBindingEvent.Key.KEY_SHIFT_LEFT || event.key == KeyBindingEvent.Key.KEY_SHIFT_RIGHT) {
                        getComponent<Magnifier>()?.apply {
                            is  = true
                            show()
                        }
                    }
                } else {
                    getComponent<Magnifier>()?.apply {
                        isShowing = false
                        hide()
                    }
                }
            }

            subscribeAlways<InlayHintClickEvent>(this) { _, event ->
                val hint = event.inlayHint
                if (hint is TextInlayHint) {
                    toast("Text inlay hint clicked: ${hint.text}")
                } else if (hint is ColorInlayHint) {
                    toast("Color inlay hint clicked: ${hint.color}")
                }
            }

            subscribeAlways<SideIconClickEvent>(this) { _, event ->
                val diagnostic = event.tag
                if (diagnostic is DiagnosticRegion) {
                    toast("Diagnostic clicked: ${diagnostic.message}")
                }
            }

            subscribeAlways<PublishSearchResultEvent>(this) { _, event ->
                toast("Found ${event.count} results")
            }
        }

        // Set initial language and theme
        setEditorLanguage(getCurrentEditor(), "java")
        setColorScheme(SchemeDarcula())

        // Register custom languages
        MonarchGrammarRegistry.getInstance().apply {
            monarchLanguages {
                language("java") {
                    source = ThemeSource.Asset("java.json")
                }
                language("kotlin") {
                    source = ThemeSource.Asset("kotlin.json")
                }
                language("python") {
                    source = ThemeSource.Asset("python.json")
                }
                language("typescript") {
                    source = ThemeSource.Asset("typescript.json")
                }
            }
        }

        GrammarRegistry.getInstance().apply {
            FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(assets))
            languages {
                language("java") {
                    grammar = DefaultGrammarDefinition.Asset("java.tmLanguage.json")
                }
                language("kotlin") {
                    grammar = DefaultGrammarDefinition.Asset("kotlin.tmLanguage.json")
                }
                language("python") {
                    grammar = DefaultGrammarDefinition.Asset("python.tmLanguage.json")
                }
                language("typescript") {
                    grammar = DefaultGrammarDefinition.Asset("typescript.tmLanguage.json")
                }
            }
        }

        ThemeRegistry.getInstance().apply {
            setTheme(
                ThemeModel.Definition.Asset("darcula.json"),
                ThemeModel.Definition.Asset("light.json")
            )
        }

        // Handle file opening
        if (intent.data != null) {
            openUri(intent.data!!)
        }
    }

    private fun getCurrentEditor(): CodeEditor {
        val currentFragment = tabAdapter.getFragment(viewPager.currentItem) as? EditorFragment
        return currentFragment?.editor ?: binding.editor // Fallback to the original editor if no tab is selected
    }

    private fun updatePositionText() {
        val editor = getCurrentEditor()
        val cursor = editor.cursor
        val text = "Line ${cursor.line + 1}, Column ${cursor.column + 1} | Chars: ${editor.text.length}"
        binding.positionDisplay.text = text
    }

    private fun setEditorLanguage(editor: CodeEditor, languageId: String) {
        when (languageId) {
            "java" -> editor.setEditorLanguage(io.github.rosemoe.sora.langs.java.JavaLanguage())
            "kotlin" -> editor.setEditorLanguage(io.github.rosemoe.sora.langs.monarch.MonarchLanguage.create("kotlin"))
            "python" -> editor.setEditorLanguage(io.github.rosemoe.sora.langs.monarch.MonarchLanguage.create("python"))
            "typescript" -> editor.setEditorLanguage(io.github.rosemoe.sora.langs.monarch.MonarchLanguage.create("typescript"))
            else -> editor.setEditorLanguage(io.github.rosemoe.sora.lang.EmptyLanguage())
        }
    }

    private fun setColorScheme(scheme: EditorColorScheme) {
        getCurrentEditor().colorScheme = scheme
    }

    private fun showSearchOptions(view: View) {
        searchMenu.show()
    }

    private fun computeSearchOptions() {
        searchOptions.is  = searchMenu.menu.findItem(R.id.search_option_regex)!!.isChecked
        searchOptions.isWholeWord = searchMenu.menu.findItem(R.id.search_option_whole_word)!!.isChecked
        searchOptions.isCaseSensitive = searchMenu.menu.findItem(R.id.search_option_case_sensitive)!!.isChecked
    }

    private fun tryCommitSearch() {
        val editor = getCurrentEditor()
        val searcher = editor.getComponent<EditorSearcher>()
        val query = binding.searchEditor.text.toString()
        if (query.isEmpty()) {
            searcher?.stopSearch()
            return
        }
        try {
            searcher?.search(query, searchOptions)
        } catch (e: PatternSyntaxException) {
            toast("Regex error: " + e.message)
        }
    }

    private fun gotoPrev(view: View) {
        getCurrentEditor().getComponent<EditorSearcher>()?.gotoPrev()
    }

    private fun gotoNext(view: View) {
        getCurrentEditor().getComponent<EditorSearcher>()?.gotoNext()
    }

    private fun replace(view: View) {
        getCurrentEditor().getComponent<EditorSearcher>()?.replace(binding.replaceEditor.text.toString())
    }

    private fun replaceAll(view: View) {
        getCurrentEditor().getComponent<EditorSearcher>()?.replaceAll(binding.replaceEditor.text.toString())
    }

    private fun openUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val text = ContentIO.readContent(this@MainActivity, uri)
                withContext(Dispatchers.Main) {
                    addTab(uri.lastPathSegment ?: "Untitled", text)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open file", e)
                withContext(Dispatchers.Main) {
                    toast("Failed to open file: ${e.message}")
                }
            }
        }
    }

    private val openFile = registerForActivityResult(GetContent()) {
        if (it != null) {
            openUri(it)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        undo = menu.findItem(R.id.menu_undo)
        redo = menu.findItem(R.id.menu_redo)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val editor = getCurrentEditor()
        when (item.itemId) {
            R.id.menu_open -> openFile.launch("*/*")
            R.id.menu_save -> saveCurrentFile()
            R.id.menu_undo -> editor.undo()
            R.id.menu_redo -> editor.redo()
            R.id.menu_wordwrap -> item.isChecked = !item.isChecked.also { editor.setWordwrap(it) }
            R.id.menu_auto_completion -> item.isChecked = !item.isChecked.also { editor.getComponent<EditorAutoCompletion>()?.isEnabled = it }
            R.id.menu_debug_lsp_java -> LspTestJavaActivity.start(this)
            R.id.menu_debug_lsp -> LspTestActivity.start(this)
            R.id.menu_debug_paged -> PagedEditActivity.start(this)
            R.id.menu_debug_test -> TestActivity.start(this)
            R.id.menu_editor_java -> setEditorLanguage(editor, "java")
            R.id.menu_editor_kotlin -> setEditorLanguage(editor, "kotlin")
            R.id.menu_editor_python -> setEditorLanguage(editor, "python")
            R.id.menu_editor_typescript -> setEditorLanguage(editor, "typescript")
            R.id.menu_scheme_darcula -> setColorScheme(SchemeDarcula())
            R.id.menu_scheme_eclipse -> setColorScheme(SchemeEclipse())
            R.id.menu_scheme_github -> setColorScheme(SchemeGitHub())
            R.id.menu_scheme_notepadxx -> setColorScheme(SchemeNotepadXX())
            R.id.menu_scheme_vs2019 -> setColorScheme(SchemeVS2019())
            R.id.menu_line_info_panel_none -> editor.lineInfoPanelPosition = LineInfoPanelPosition.NONE
            R.id.menu_line_info_panel_start -> editor.lineInfoPanelPosition = LineInfoPanelPosition.START
            R.id.menu_line_info_panel_end -> editor.lineInfoPanelPosition = LineInfoPanelPosition.END
            R.id.menu_line_info_panel_center -> editor.lineInfoPanelPosition = LineInfoPanelPosition.CENTER
            R.id.menu_line_info_panel_follow -> editor.lineInfoPanelPositionMode = LineInfoPanelPositionMode.FOLLOW
            R.id.menu_line_info_panel_fixed -> editor.lineInfoPanelPositionMode = LineInfoPanelPositionMode.FIXED
            R.id.menu_insert_color_inlay_hint -> editor.getComponent<InlayHintsContainer>()?.addInlayHint(ColorInlayHint(editor.cursor.left, editor.cursor.top, 0xFFFF0000.toInt()))
            R.id.menu_insert_text_inlay_hint -> editor.getComponent<InlayHintsContainer>()?.addInlayHint(TextInlayHint(editor.cursor.left, editor.cursor.top, "Hello"))
            R.id.menu_clear_inlay_hints -> editor.getComponent<InlayHintsContainer>()?.clearInlayHints()
            R.id.menu_add_diagnostic -> editor.getComponent<DiagnosticsContainer>()?.addDiagnostic(DiagnosticRegion(editor.cursor.left, editor.cursor.top, editor.cursor.left + 5, editor.cursor.top, "Error", "This is a test error"))
            R.id.menu_clear_diagnostics -> editor.getComponent<DiagnosticsContainer>()?.clearDiagnostics()
            R.id.menu_span_test -> editor.getComponent<EditorSpanInteractionHandler>()?.apply {
                if (isEnabled) {
                    disable()
                    toast("Span interaction disabled")
                } else {
                    enable()
                    toast("Span interaction enabled")
                }
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val editor = getCurrentEditor()
        undo?.isEnabled = editor.canUndo()
        redo?.isEnabled = editor.canRedo()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onBackPressed() {
        if (binding.searchPanel.visibility == View.VISIBLE) {
            binding.searchPanel.visibility = View.GONE
            return
        }
        super.onBackPressed()
    }

    override fun onPause() {
        super.onPause()
        saveTabs()
    }

    private fun saveCurrentFile() {
        val currentFragment = tabAdapter.getFragment(viewPager.currentItem) as? EditorFragment
        currentFragment?.let { fragment ->
            val tabInfo = tabs[viewPager.currentItem]
            // Here you would implement actual file saving logic if the tab is associated with a file
            // For now, we just save the content to preferences
            tabInfo.content = fragment.editor.text.toString()
            saveTabs()
            toast("Tab content saved to preferences")
        }
    }

    private fun addTab(title: String, content: String) {
        val newTab = TabInfo(currentTabId++, title, content)
        tabs.add(newTab)
        tabAdapter.notifyItemInserted(tabs.size - 1)
        viewPager.currentItem = tabs.size - 1
        saveTabs()
    }

    private fun removeTab(position: Int) {
        if (tabs.size > 1) {
            tabs.removeAt(position)
            tabAdapter.notifyItemRemoved(position)
            saveTabs()
        } else {
            toast("Cannot remove the last tab")
        }
    }

    private fun showDeleteTabDialog(position: Int) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Tab")
            .setMessage("Are you sure you want to delete tab '${tabs[position].title}'?")
            .setPositiveButton("Delete") { dialog, _ ->
                removeTab(position)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showRenameTabDialog(position: Int) {
        val tabInfo = tabs[position]
        val input = EditText(this)
        input.setText(tabInfo.title)

        MaterialAlertDialogBuilder(this)
            .setTitle("Rename Tab")
            .setView(input)
            .setPositiveButton("Rename") { dialog, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    tabInfo.title = newName
                    tabLayout.getTabAt(position)?.text = newName
                    saveTabs()
                } else {
                    toast("Tab name cannot be empty")
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun saveTabs() {
        val sharedPreferences = getSharedPreferences("sora_editor_tabs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val gson = Gson()
        val json = gson.toJson(tabs)
        editor.putString("tabs_list", json)
        editor.putLong("current_tab_id", currentTabId)
        editor.apply()
    }

    private fun loadTabs() {
        val sharedPreferences = getSharedPreferences("sora_editor_tabs", Context.MODE_PRIVATE)
        val gson = Gson()
        val json = sharedPreferences.getString("tabs_list", null)
        val type = object : TypeToken<MutableList<TabInfo>>() {}.type
        val loadedTabs: MutableList<TabInfo>? = gson.fromJson(json, type)
        if (loadedTabs != null) {
            tabs.clear()
            tabs.addAll(loadedTabs)
            currentTabId = sharedPreferences.getLong("current_tab_id", 0L)
        }
    }

    data class TabInfo(var id: Long, var title: String, var content: String)

    class TabAdapter(fragmentActivity: FragmentActivity, private val tabs: MutableList<TabInfo>) : FragmentStateAdapter(fragmentActivity) {

        private val fragmentMap = mutableMapOf<Int, EditorFragment>()

        override fun getItemCount(): Int = tabs.size

        override fun createFragment(position: Int): Fragment {
            val fragment = EditorFragment.newInstance(tabs[position].content)
            fragmentMap[position] = fragment
            return fragment
        }

        fun getFragment(position: Int): Fragment? {
            return fragmentMap[position]
        }
    }
}
