package com.chatwaifu.mobile.ui

import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.chatwaifu.chatgpt.ChatGPTResponseData
import com.chatwaifu.live2d.GLRenderer
import com.chatwaifu.live2d.JniBridgeJava
import com.chatwaifu.mobile.ChatActivityViewModel
import com.chatwaifu.mobile.databinding.FragmentHomeBinding

class ChatFragment : Fragment() {
    companion object {
        private const val TAG = "ChatFragment"
    }

    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private val viewModel: ChatActivityViewModel by activityViewModels()
    private val listAdapter = ChatListAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        initView()
        return binding.root
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
        viewModel.chatResponseLiveData.observe(viewLifecycleOwner){
            onReceiveResponse(it)
        }
        viewModel.generateSoundLiveData.observe(viewLifecycleOwner){isSuccess->
            if (!isSuccess) {
                showToast("sound generate failed....")
            }
        }
        viewModel.chatStatusLiveData.observe(viewLifecycleOwner){status ->
            when (status) {
                ChatActivityViewModel.ChatStatus.SEND_REQUEST -> {
                    showToast("send request...")
                }
                ChatActivityViewModel.ChatStatus.GENERATE_SOUND ->{
                    showToast("generating sound...")
                }
                else ->{
                    //ignored
                }
            }
        }
        binding.chatLogList.adapter = listAdapter
        binding.chatLogList.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun onSendBtnClick() {
        if (viewModel.chatStatusLiveData.value != ChatActivityViewModel.ChatStatus.FETCH_INPUT) {
            showToast("can't send msg now...")
            return
        }
        val sendText = binding.inputEditText.text.toString()
        if (sendText.isNotBlank()) {
            Log.d(TAG, "try to send msg $sendText")
            viewModel.sendMessage(sendText)
            listAdapter.items.add(ListBean(isGpt = false, sendText))
            listAdapter.notifyItemChanged(listAdapter.items.size - 1)
            binding.inputEditText.clearFocus()
            binding.inputEditText.setText("")
        }
    }

    private fun onReceiveResponse(response: ChatGPTResponseData?) {
        val result = response?.choices?.firstOrNull()?.text
        if (result.isNullOrEmpty()) {
            showToast("Error occur...ChatGPT response empty")
            return
        }
        listAdapter.items.add(ListBean(isGpt = true, result))
        listAdapter.notifyItemChanged(listAdapter.items.size - 1)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onStart() {
        super.onStart()
        JniBridgeJava.nativeOnStart()
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