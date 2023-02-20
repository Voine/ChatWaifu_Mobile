package com.chatwaifu.mobile.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.chatwaifu.mobile.ChatActivityViewModel
import com.chatwaifu.mobile.R
import com.chatwaifu.mobile.databinding.BaiduTranslateKeyDialogBinding

/**
 * Description: PrivateKeyDialogFragment
 * Author: Voine
 * Date: 2023/2/19
 */
class BaiduTranslateKeyDialogFragment : DialogFragment() {

    private val viewModel: ChatActivityViewModel by activityViewModels()
    lateinit var binding: BaiduTranslateKeyDialogBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = BaiduTranslateKeyDialogBinding.inflate(inflater, container, false)
        initView()
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun initView() {
        binding.inputConfirmBtn.setOnClickListener {
            val inputId = binding.inputAppid.text.toString()
            val inputKey = binding.inputKey.text.toString()
            if (inputId.isNotBlank() && inputKey.isNotBlank()) {
                viewModel.setBaiduTranslate(inputId, inputKey)
                dismiss()
                return@setOnClickListener
            }
            showToast("Error inputId or inputKey")
        }
    }


    companion object {
        fun newInstance(): BaiduTranslateKeyDialogFragment {
            return BaiduTranslateKeyDialogFragment()
        }
    }
}