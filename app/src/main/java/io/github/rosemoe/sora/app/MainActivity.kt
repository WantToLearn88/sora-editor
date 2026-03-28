/*******************************************************************************
 *    sora-editor - the awesome code editor for Android
 *    https://github.com/Rosemoe/sora-editor
 *    Copyright (C) 2020-2024  Rosemoe
 *
 *     This library is free software; you can redistribute it and/or
 *     modify it under the terms of the GNU Lesser General Public
 *     License as published by the Free Software Foundation; either
 *     version 2.1 of the License, or (at your option) any later version.
 *
 *     This library is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *     Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public
 *     License along with this library; if not, write to the Free Software
 *     Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 *     USA
 *
 *     Please contact Rosemoe by email 2073412493@qq.com if you need
 *     additional information or have any questions
 ******************************************************************************/
package io.github.rosemoe.sora.app

import android.content.Context
import android.content.DialogInterface
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.github.dingyi222666.monarch.languages.JavaLanguage
import io.github.dingyi222666.monarch.languages.KotlinLanguage
import io.github.dingyi222666.monarch.languages.PythonLanguage
import io.github.dingyi222666.monarch.languages.TypescriptLanguage
import io.github.rosemoe.sora.app.databinding.ActivityMainBinding
import io.github.rosemoe.sora.app.lsp.LspTestActivity
import io.github.rosemoe.sora.app.lsp.LspTestJavaActivity
import io.github.rosemoe.sora.app.tests.TestActivity
import io.github.rosemoe.sora.app.tests.paged.PagedEditActivity
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.EditorKeyEvent
import io.github.rosemoe.sora.event.InlayHintClickEvent
import io.github.rosemoe.sora.event.KeyBindingEvent
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.event.SideIconClickEvent
import io.github.rosemoe.sora.event.TextSizeChangeEvent
import io.github.rosemoe.sora.graphics.inlayHint.ColorInlayHintRenderer
import io.github.rosemoe.sora.graphics.inlayHint.TextInlayHintRenderer
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.JavaLanguageSpec
import io.github.rosemoe.sora.lang.TsLanguageJava
import io.github.rosemoe.sora.lang.analysis.AsyncIncrementalAnalyzeManager
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.github.rosemoe.sora.lang.styling.color.ConstColor
import io.github.rosemoe.sora.lang.styling.inlayHint.ColorInlayHint
import io.github.rosemoe.sora.lang.styling.inlayHint.InlayHintsContainer
import io.github.rosemoe.sora.lang.styling.inlayHint.TextInlayHint
import io.github.rosemoe.sora.langs.java.JavaLanguage as JavaLanguageV2
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
import io.github.rosemoe.sora.util.regex.RegexBackrefGrammar
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
import java.util.UUID

// كلاس لتمثيل بيانات التبويب
data class EditorTab(
    var id: String = UUID.randomUUID().toString(),
    var title: String,
    var content: String = ""
)

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

    // --- Tab System Variables ---
    private val tabs = mutableListOf<EditorTab>()
    private val gson = Gson()
    private var isUpdatingTabs = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashHandler.INSTANCE.init(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.activityToolbar)
        applyEdgeToEdge(this, binding.toolbarContainer, binding.root)

        val typeface = Typeface.createFromAsset(assets, "JetBrainsMono-Regular.ttf")

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
        inputView.bindEditor(binding.editor)
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
        binding.editor.apply {
            registerInlayHintRenderers(
                TextInlayHintRenderer.DefaultInstance,
                ColorInlayHintRenderer.DefaultInstance
            )
            typefaceText = typeface
            props.deleteMultiSpaces = 1
            setLineSpacing(2f, 1.1f)
            nonPrintablePaintingFlags =
                CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or CodeEditor.FLAG_DRAW_LINE_SEPARATOR or CodeEditor.FLAG_DRAW_WHITESPACE_INNER

            subscribeAlways(EditorKeyEvent::class.java) {
                if (it.keyCode == KeyEvent.KEYCODE_S && it.isCtrlPressed) {
                    toast("Save shortcut pressed")
                    it.intercept()
                }
            }

            subscribeAlways(ContentChangeEvent::class.java) {
                updateBtnState()
                updatePositionText()
                // --- Tab Content Update ---
                if (!isUpdatingTabs) {
                    val currentIdx = binding.tabLayout.selectedTabPosition
                    if (currentIdx != -1 && currentIdx < tabs.size) {
                        tabs[currentIdx].content = text.toString()
                        saveTabs()
                    }
                }
            }

            subscribeAlways(SelectionChangeEvent::class.java) {
                updatePositionText()
            }

            subscribeAlways(PublishSearchResultEvent::class.java) {
                updatePositionText()
            }

            subscribeAlways(TextSizeChangeEvent::class.java) {
                toast("Text size changed to ${it.newSize}")
            }

            subscribeAlways(SideIconClickEvent::class.java) {
                toast("Side icon clicked at line ${it.line}")
            }

            subscribeAlways(InlayHintClickEvent::class.java) {
                toast("Inlay hint clicked: ${it.renderer}")
            }

            postDelayedInLifecycle(500) {
                // Setup languages and themes
                setupLanguages()
                setupDiagnostics()

                // Default language and theme
                setEditorLanguage(JavaLanguageV2())
                colorScheme = SchemeDarcula()

                // Load initial file (Modified to support Tabs)
                loadTabs()
                if (tabs.isEmpty()) {
                    addNewTab("New File.java", "public class Main {\n\n}")
                } else {
                    setupTabLayoutUI()
                }
            }
        }
    }

    // --- Tab System Implementation ---

    private fun loadTabs() {
        val prefs = getSharedPreferences("editor_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("saved_tabs", null)
        if (json != null) {
            try {
                val type = object : TypeToken<MutableList<EditorTab>>() {}.type
                val savedTabs: MutableList<EditorTab> = gson.fromJson(json, type)
                tabs.clear()
                tabs.addAll(savedTabs)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun saveTabs() {
        val prefs = getSharedPreferences("editor_prefs", Context.MODE_PRIVATE)
        val json = gson.toJson(tabs)
        prefs.edit().putString("saved_tabs", json).apply()
    }

    private fun setupTabLayoutUI() {
        binding.tabLayout.removeAllTabs()
        for (tabData in tabs) {
            binding.tabLayout.addTab(binding.tabLayout.newTab().setText(tabData.title))
        }
        
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.let {
                    val position = it.position
                    if (position < tabs.size) {
                        isUpdatingTabs = true
                        binding.editor.setText(tabs[position].content)
                        isUpdatingTabs = false
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Initial load of first tab
        if (tabs.isNotEmpty()) {
            binding.editor.setText(tabs[0].content)
        }

        binding.tabLayout.post { setupTabLongClicks() }
    }

    private fun setupTabLongClicks() {
        val tabStrip = binding.tabLayout.getChildAt(0) as ViewGroup
        for (i in 0 until tabStrip.childCount) {
            tabStrip.getChildAt(i).setOnLongClickListener {
                showTabOptionsDialog(i)
                true
            }
        }
    }

    private fun addNewTab(title: String, content: String = "") {
        val newTab = EditorTab(title = title, content = content)
        tabs.add(newTab)
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(title))
        binding.tabLayout.selectTab(binding.tabLayout.getTabAt(tabs.size - 1))
        saveTabs()
        binding.tabLayout.post { setupTabLongClicks() }
    }

    private fun showTabOptionsDialog(position: Int) {
        val options = arrayOf("إعادة تسمية", "حذف التبويب", "تبويب جديد")
        MaterialAlertDialogBuilder(this)
            .setTitle("خيارات التبويب: ${tabs[position].title}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenameDialog(position)
                    1 -> showDeleteConfirmDialog(position)
                    2 -> showAddNewTabDialog()
                }
            }
            .show()
    }

    private fun showAddNewTabDialog() {
        val input = EditText(this)
        input.hint = "اسم الملف الجديد"
        MaterialAlertDialogBuilder(this)
            .setTitle("إضافة تبويب جديد")
            .setView(input)
            .setPositiveButton("إضافة") { _, _ ->
                val name = input.text.toString()
                if (name.isNotEmpty()) addNewTab(name)
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showRenameDialog(position: Int) {
        val input = EditText(this)
        input.setText(tabs[position].title)
        MaterialAlertDialogBuilder(this)
            .setTitle("إعادة تسمية")
            .setView(input)
            .setPositiveButton("حفظ") { _, _ ->
                val newName = input.text.toString()
                if (newName.isNotEmpty()) {
                    tabs[position].title = newName
                    binding.tabLayout.getTabAt(position)?.text = newName
                    saveTabs()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showDeleteConfirmDialog(position: Int) {
        if (tabs.size <= 1) {
            toast("لا يمكن حذف آخر تبويب")
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("حذف")
            .setMessage("هل تريد حذف '${tabs[position].title}'؟")
            .setPositiveButton("حذف") { _, _ ->
                tabs.removeAt(position)
                binding.tabLayout.removeTabAt(position)
                saveTabs()
                binding.tabLayout.post { setupTabLongClicks() }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    // --- Original Helper Methods (Preserved) ---

    private fun tryCommitSearch() {
        val text = binding.searchEditor.text.toString()
        if (text.isEmpty()) {
            binding.editor.searcher.stopSearch()
        } else {
            try {
                binding.editor.searcher.search(text, searchOptions)
            } catch (e: PatternSyntaxException) {
                // Ignore
            }
        }
    }

    private fun computeSearchOptions() {
        val regex = searchMenu.menu.findItem(R.id.search_option_regex).isChecked
        val matchCase = searchMenu.menu.findItem(R.id.search_option_match_case).isChecked
        val wholeWord = searchMenu.menu.findItem(R.id.search_option_whole_word).isChecked
        searchOptions = SearchOptions(regex, matchCase, wholeWord)
    }

    private fun setupLanguages() {
        FileProviderRegistry.getInstance().addFileResolver(AssetsFileResolver(assets))
        GrammarRegistry.getInstance().loadLanguages(
            languages {
                language("java") {
                    grammar = "textmate/java/syntaxes/java.tmLanguage.json"
                    defaultScopeName()
                    languageConfiguration = "textmate/java/language-configuration.json"
                }
                language("kotlin") {
                    grammar = "textmate/kotlin/syntaxes/Kotlin.tmLanguage.json"
                    defaultScopeName()
                    languageConfiguration = "textmate/kotlin/language-configuration.json"
                }
                language("python") {
                    grammar = "textmate/python/syntaxes/python.tmLanguage.json"
                    defaultScopeName()
                    languageConfiguration = "textmate/python/language-configuration.json"
                }
            }
        )
    }

    private fun resetColorScheme() {
        binding.editor.apply {
            val colorScheme = this.colorScheme
            this.colorScheme = colorScheme
        }
    }

    private fun setupDiagnostics() {
        val editor = binding.editor
        val container = DiagnosticsContainer()
        for (i in 0 until editor.text.lineCount) {
            val index = editor.text.getCharIndex(i, 0)
            container.addDiagnostic(
                DiagnosticRegion(
                    index,
                    index + editor.text.getColumnCount(i),
                    DiagnosticRegion.SEVERITY_ERROR
                )
            )
        }
        editor.diagnostics = container
    }

    private fun ensureTextmateTheme() {
        val editor = binding.editor
        var editorColorScheme = editor.colorScheme
        if (editorColorScheme !is TextMateColorScheme) {
            editorColorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
            editor.colorScheme = editorColorScheme
        }
    }

    private fun ensureMonarchTheme() {
        val editor = binding.editor
        var editorColorScheme = editor.colorScheme
        if (editorColorScheme !is MonarchColorScheme) {
            editorColorScheme = MonarchColorScheme.create(io.github.rosemoe.sora.langs.monarch.registry.ThemeRegistry.currentTheme)
            editor.colorScheme = editorColorScheme
            switchThemeIfRequired(this, editor)
        }
    }

    private fun updateBtnState() {
        undo?.isEnabled = binding.editor.canUndo()
        redo?.isEnabled = binding.editor.canRedo()
    }

    private fun updatePositionText() {
        val cursor = binding.editor.cursor
        var text = (1 + cursor.leftLine).toString() + ":" + cursor.leftColumn + ";" + cursor.left + " "
        text += if (cursor.isSelected) {
            "(" + (cursor.right - cursor.left) + " chars)"
        } else {
            val content = binding.editor.text
            if (content.getColumnCount(cursor.leftLine) == cursor.leftColumn) {
                "(<" + content.getLine(cursor.leftLine).lineSeparator.let {
                    if (it == LineSeparator.NONE) "EOF" else it.name
                } + ">)"
            } else {
                "(" + content.getLine(cursor.leftLine).codePointStringAt(cursor.leftColumn).escapeCodePointIfNecessary() + ")"
            }
        }
        val searcher = binding.editor.searcher
        if (searcher.hasQuery()) {
            val idx = searcher.currentMatchedPositionIndex
            val count = searcher.matchedPositionCount
            val matchText = if (count == 0) "no match" else if (count == 1) "1 match" else "$count matches"
            text += if (idx == -1) "($matchText)" else "(${idx + 1} of $matchText)"
        }
        binding.positionDisplay.text = text
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.apply {
            putString("text", binding.editor.text.toString())
            putFloat("font.size", binding.editor.textSizePx)
            putInt("position.left", binding.editor.cursor.left)
            putInt("position.right", binding.editor.cursor.right)
            putInt("scroll.x", binding.editor.offsetX)
            putInt("scroll.y", binding.editor.offsetY)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        undo = menu.findItem(R.id.text_undo)
        redo = menu.findItem(R.id.text_redo)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.editor.release()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        val editor = binding.editor
        when (id) {
            R.id.open_test_activity -> startActivity<TestActivity>()
            R.id.open_paged_edit -> startActivity<PagedEditActivity>()
            R.id.text_undo -> editor.undo()
            R.id.text_redo -> editor.redo()
            R.id.goto_end -> editor.setSelection(editor.text.lineCount - 1, editor.text.getColumnCount(editor.text.lineCount - 1))
            R.id.move_up -> editor.moveSelection(SelectionMovement.UP)
            R.id.move_down -> editor.moveSelection(SelectionMovement.DOWN)
            R.id.home -> editor.moveSelection(SelectionMovement.LINE_START)
            R.id.end -> editor.moveSelection(SelectionMovement.LINE_END)
            R.id.move_left -> editor.moveSelection(SelectionMovement.LEFT)
            R.id.move_right -> editor.moveSelection(SelectionMovement.RIGHT)
            R.id.magnifier -> {
                item.isChecked = !item.isChecked
                editor.getComponent(Magnifier::class.java).isEnabled = item.isChecked
            }
        }
        return super.onOptionsItemSelected(item)
    }
}
