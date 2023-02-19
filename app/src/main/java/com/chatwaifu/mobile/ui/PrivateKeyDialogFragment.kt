package com.chatwaifu.mobile.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.chatwaifu.mobile.ChatActivityViewModel
import com.chatwaifu.mobile.databinding.PrivateKeyDialogBinding

/**
 * Description: PrivateKeyDialogFragment
 * Author: Voine
 * Date: 2023/2/19
 */
class PrivateKeyDialogFragment : DialogFragment() {

    private val viewModel: ChatActivityViewModel by activityViewModels()
    lateinit var binding: PrivateKeyDialogBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = PrivateKeyDialogBinding.inflate(inflater, container, false)
        initView()
        return binding.root
    }

    private fun initView() {
        binding.inputConfirmBtn.setOnClickListener {
            val currentText = binding.inputPrivateKey.text.toString()
            if (currentText.isBlank()) {
                showToast("empty key...")
                return@setOnClickListener
            }
            viewModel.setPrivateKey(currentText)
            dismiss()
        }
    }


    companion object{
        fun newInstance(): PrivateKeyDialogFragment {
            return PrivateKeyDialogFragment()
        }
    }
}