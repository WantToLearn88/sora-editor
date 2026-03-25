package io.github.rosemoe.sora.app

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import io.github.rosemoe.sora.app.databinding.FragmentEditorBinding
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.EditorKeyEvent
import io.github.rosemoe.sora.event.InlayHintClickEvent
import io.github.rosemoe.sora.event.KeyBindingEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.event.SideIconClickEvent
import io.github.rosemoe.sora.event.TextSizeChangeEvent
import io.github.rosemoe.sora.graphics.inlayHint.ColorInlayHintRenderer
import io.github.rosemoe.sora.graphics.inlayHint.TextInlayHintRenderer
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.styling.inlayHint.ColorInlayHint
import io.github.rosemoe.sora.lang.styling.inlayHint.TextInlayHint
import io.github.rosemoe.sora.utils.toast
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.SelectionMovement
import io.github.rosemoe.sora.widget.component.Magnifier
import io.github.rosemoe.sora.widget.getComponent
import io.github.rosemoe.sora.widget.subscribeAlways
import android.view.KeyEvent

class EditorFragment : Fragment() {

    private var _binding: FragmentEditorBinding? = null
    private val binding get() = _binding!!
    lateinit var editor: CodeEditor

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditorBinding.inflate(inflater, container, false)
        editor = binding.editor
        setupEditor()
        
        val content = arguments?.getString(ARG_CONTENT) ?: ""
        editor.setText(content)
        
        return binding.root
    }

    private fun setupEditor() {
        val typeface = Typeface.createFromAsset(requireContext().assets, "JetBrainsMono-Regular.ttf")
        editor.apply {
            registerInlayHintRenderers(
                TextInlayHintRenderer.DefaultInstance,
                ColorInlayHintRenderer.DefaultInstance
            )
            typefaceText = typeface
            props.stickyScroll = true
            setLineSpacing(2f, 1.1f)
            nonPrintablePaintingFlags =
                CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or CodeEditor.FLAG_DRAW_LINE_SEPARATOR or CodeEditor.FLAG_DRAW_WHITESPACE_IN_SELECTION or CodeEditor.FLAG_DRAW_SOFT_WRAP

            // Subscribe to events
            subscribeAlways<ContentChangeEvent>(viewLifecycleOwner) { _, _ ->
                (activity as? MainActivity)?.onEditorContentChanged(this.text.toString())
            }

            subscribeAlways<SelectionChangeEvent>(viewLifecycleOwner) { _, _ ->
                (activity as? MainActivity)?.updatePositionText()
            }

            subscribeAlways<TextSizeChangeEvent>(viewLifecycleOwner) { _, _ ->
                (activity as? MainActivity)?.updatePositionText()
            }

            subscribeAlways<EditorKeyEvent>(viewLifecycleOwner) { _, event ->
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
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CONTENT = "content"

        fun newInstance(content: String): EditorFragment {
            val fragment = EditorFragment()
            val args = Bundle()
            args.putString(ARG_CONTENT, content)
            fragment.arguments = args
            return fragment
        }
    }
}
