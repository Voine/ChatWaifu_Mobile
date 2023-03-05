package com.chatwaifu.mobile.ui.chat

import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.chatwaifu.chatgpt.ChatGPTResponseData
import com.chatwaifu.live2d.GLRenderer
import com.chatwaifu.live2d.JniBridgeJava
import com.chatwaifu.mobile.ChatActivityViewModel
import com.chatwaifu.mobile.data.Constant
import com.chatwaifu.mobile.databinding.FragmentHomeBinding
import com.chatwaifu.mobile.ui.showToast
import java.io.File

class ChatFragment : Fragment() {
    companion object {
        private const val TAG = "ChatFragment"
    }

    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private val viewModel: ChatActivityViewModel by activityViewModels()
    private val softInputManager: InputMethodManager? by lazy {
        context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView()
    }

    private fun initView() {
        JniBridgeJava.SetActivityInstance(requireActivity())
        JniBridgeJava.SetContext(requireContext())
        binding.live2dRenderView.setEGLContextClientVersion(2)
        binding.live2dRenderView.setRenderer(GLRenderer())
        binding.live2dRenderView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        binding.sendMsgButton.setOnClickListener {
            onSendBtnClick()
        }
        viewModel.chatResponseLiveData.observe(viewLifecycleOwner) {
            onReceiveResponse(it)
        }
        viewModel.generateSoundLiveData.observe(viewLifecycleOwner) { isSuccess ->
            if (!isSuccess) {
                showToast("sound generate failed....")
            }
        }
        viewModel.chatStatusLiveData.observe(viewLifecycleOwner) { status ->
            when (status) {
                ChatActivityViewModel.ChatStatus.SEND_REQUEST -> {
                    binding.showMsgResponseSate.text = "send request..."
                }
                ChatActivityViewModel.ChatStatus.GENERATE_SOUND -> {
                    binding.showMsgResponseSate.text = "generating sound..."

                }
                ChatActivityViewModel.ChatStatus.TRANSLATE -> {
                    binding.showMsgResponseSate.text = "translating...."
                }
                else -> {
                    binding.showMsgResponseSate.text = ""
                }
            }
        }
    }

    private fun onSendBtnClick() {
        val sendText = binding.inputEditText.text.toString()
        if (sendText.isNotBlank()) {
            Log.d(TAG, "try to send msg $sendText")
            updateSendText(sendText)
            viewModel.sendMessage(sendText)
            binding.inputEditText.setText("")
            softInputManager?.hideSoftInputFromWindow(binding.root.windowToken, 0)
        }
    }


    private fun updateSendText(sendText: String) {
        binding.showMsgCharacter.text = "Me:"
        binding.showMsgText.text = sendText
    }


    private fun onReceiveResponse(response: ChatGPTResponseData?) {
        val result = response?.choices?.firstOrNull()?.message?.content
        if (result.isNullOrEmpty()) {
            showToast("Error occur...ChatGPT response empty")
            return
        }
        binding.showMsgCharacter.text = "${viewModel.currentLive2DModelName}:"
        binding.showMsgText.text = result.trim()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onStart() {
        super.onStart()
        JniBridgeJava.nativeOnStart()
        val jsonFileName = "${viewModel.currentLive2DModelName}.model3.json"
        if (!File(viewModel.currentLive2DModelPath + File.separator, jsonFileName).exists()) {
            showToast("cant find model3.json...")
            return
        }
        JniBridgeJava.nativeProjectChangeTo(
            viewModel.currentLive2DModelPath + File.separator,
            jsonFileName
        )
        if (viewModel.currentLive2DModelName == Constant.LOCAL_MODEL_KURISU) {
            //fix kurisu live2d bug..
            JniBridgeJava.needRenderBack(false)
            JniBridgeJava.nativeApplyExpression("fix")
        }
    }



    override fun onResume() {
        super.onResume()
        binding.live2dRenderView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.live2dRenderView.onPause()
        JniBridgeJava.nativeOnPause()
    }

    override fun onStop() {
        super.onStop()
        JniBridgeJava.nativeOnStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        JniBridgeJava.nativeOnDestroy()
    }
}