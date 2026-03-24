package io.github.rosemoe.sora.app.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import io.github.rosemoe.sora.app.databinding.FragmentEditorBinding
import io.github.rosemoe.sora.widget.CodeEditor

class EditorFragment : Fragment() {

    private var _binding: FragmentEditorBinding? = null
    private val binding get() = _binding!!
    lateinit var editor: CodeEditor

    private var initialContent: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            initialContent = it.getString(ARG_CONTENT, "")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentEditorBinding.inflate(inflater, container, false)
        editor = binding.editor
        editor.setText(initialContent)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CONTENT = "content"

        @JvmStatic
        fun newInstance(content: String) = EditorFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_CONTENT, content)
            }
        }
    }
}
