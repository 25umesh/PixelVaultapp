package com.example.pixelvault.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.pixelvault.R
import com.example.pixelvault.databinding.FragmentChooserBinding
import com.example.pixelvault.ui.main.decode.DecodeFragment
import com.example.pixelvault.ui.main.encode.EncodeFragment

class ChooserFragment : Fragment() {

    private var _binding: FragmentChooserBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChooserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnGoToEncode.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.container, EncodeFragment()) // Corrected to R.id.container
                .addToBackStack(null)
                .commit()
        }

        binding.btnGoToDecode.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.container, DecodeFragment()) // Corrected to R.id.container
                .addToBackStack(null)
                .commit()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
