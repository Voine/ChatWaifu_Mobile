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
import com.chatwaifu.mobile.R
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

        sp.getString(Constant.SAVED_HIYORI_SETTING, null)?.let {
            if (it.isNotBlank()) {
                binding.defaultHiyoriSetting.setText(it)
            }
        } ?: binding.defaultHiyoriSetting.setText(resources.getString(R.string.default_system_hiyori))

        sp.getString(Constant.SAVED_AMADEUS_SETTING, null)?.let {
            if (it.isNotBlank()) {
                binding.defaultAmadeusSetting.setText(it)
            }
        } ?: binding.defaultAmadeusSetting.setText(resources.getString(R.string.default_system_amadeus))

        sp.getString(Constant.SAVED_ATRI_SETTING, null)?.let {
            if (it.isNotBlank()) {
                binding.defaultAtriSetting.setText(it)
            }
        } ?: binding.defaultAtriSetting.setText(resources.getString(R.string.default_system_atri))


        sp.getString(Constant.SAVED_EXTERNAL_SETTING, null)?.let {
            if (it.isNotBlank()) {
                binding.defaultExternalSetting.setText(it)
            }
        }

        sp.getBoolean(Constant.SAVED_USE_TRANSLATE, true).let {
            binding.useTranslateSwitch.isChecked = it
            updateTranslateSetting(it)
        }

        binding.useTranslateSwitch.setOnCheckedChangeListener { _, isChecked ->
            updateTranslateSetting(isChecked)
        }

        binding.inputConfirmBtn.setOnClickListener {
            val inputAppId = binding.inputAppid.text.toString()
            val inputTranslateKey = binding.inputTranslateKey.text.toString()
            val inputChatKey = binding.inputChatKey.text.toString()
            val inputHiyoriSetting = binding.defaultHiyoriSetting.text.toString()
            val inputAmadeusSetting = binding.defaultAmadeusSetting.text.toString()
            val inputATRISetting = binding.defaultAtriSetting.text.toString()
            val inputExternalSetting = binding.defaultExternalSetting.text.toString()
            sp.edit().apply {
                if (inputChatKey.isNotBlank()) {
                    putString(Constant.SAVED_CHAT_KEY, inputChatKey)
                }
                if (inputAppId.isNotBlank() && inputTranslateKey.isNotBlank()) {
                    putString(Constant.SAVED_TRANSLATE_APP_ID, inputAppId)
                    putString(Constant.SAVED_TRANSLATE_KEY, inputTranslateKey)
                }
                if (inputHiyoriSetting.isNotBlank()) {
                    putString(Constant.SAVED_HIYORI_SETTING, inputHiyoriSetting)
                }
                if (inputAmadeusSetting.isNotBlank()) {
                    putString(Constant.SAVED_AMADEUS_SETTING, inputAmadeusSetting)
                }

                if (inputATRISetting.isNotBlank()) {
                    putString(Constant.SAVED_ATRI_SETTING, inputATRISetting)
                }

                if (inputExternalSetting.isNotBlank()) {
                    putString(Constant.SAVED_EXTERNAL_SETTING, inputExternalSetting)
                }

                putBoolean(Constant.SAVED_USE_TRANSLATE, binding.useTranslateSwitch.isChecked)
                apply()
            }

            viewModel.refreshAllKeys()
            showToast("save key success...")
        }
    }

    private fun updateTranslateSetting(needTranslate: Boolean) {
        viewModel.needTranslate = needTranslate
        binding.translateViewGroup.visibility = if (needTranslate) View.VISIBLE else View.GONE
    }
}