package com.chatwaifu.mobile.ui.setting

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.chatwaifu.mobile.ChatActivityViewModel
import com.chatwaifu.mobile.data.Constant
import com.chatwaifu.mobile.databinding.SettingKeyDialogBinding
import com.chatwaifu.mobile.ui.showToast

class SettingFragment : DialogFragment() {

    private val viewModel: ChatActivityViewModel by activityViewModels()
    private val sp: SharedPreferences by lazy {
        requireActivity().getSharedPreferences(Constant.SAVED_STORE, Context.MODE_PRIVATE)
    }
    lateinit var binding: SettingKeyDialogBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = SettingKeyDialogBinding.inflate(inflater, container, false)
        initView()
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    private fun initView() {
        sp.getString(Constant.SAVED_CHAT_KEY, null)?.let {
            if(it.isNotBlank()){
                binding.inputChatKey.setText(it)
            }
        }
        sp.getString(Constant.SAVED_TRANSLATE_APP_ID, null)?.let {
            if (it.isNotBlank()) {
                binding.inputAppid.setText(it)
            }
        }
        sp.getString(Constant.SAVED_TRANSLATE_KEY, null)?.let {
            if (it.isNotBlank()) {
                binding.inputTranslateKey.setText(it)
            }
        }

        binding.inputConfirmBtn.setOnClickListener {
            val inputAppId = binding.inputAppid.text.toString()
            val inputTranslateKey = binding.inputTranslateKey.text.toString()
            val inputChatKey = binding.inputChatKey.text.toString()
            if (inputChatKey.isNotBlank()) {
                sp.edit().putString(Constant.SAVED_CHAT_KEY, inputChatKey).apply()
            }
            if (inputAppId.isNotBlank() && inputTranslateKey.isNotBlank()) {
                sp.edit().apply {
                    putString(Constant.SAVED_TRANSLATE_APP_ID, inputAppId)
                    putString(Constant.SAVED_TRANSLATE_KEY, inputTranslateKey)
                    apply()
                }
            }
            viewModel.refreshAllKeys()
            showToast("save key success...")
        }
    }
}