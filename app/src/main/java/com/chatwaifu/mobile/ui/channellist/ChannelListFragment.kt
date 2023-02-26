package com.chatwaifu.mobile.ui.channellist

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import com.chatwaifu.mobile.ChatActivity
import com.chatwaifu.mobile.ChatActivityViewModel
import com.chatwaifu.mobile.data.Constant
import com.chatwaifu.mobile.data.Constant.SAVED_FLAG_NEED_COPY_DATA
import com.chatwaifu.mobile.databinding.FragmentChannelListBinding
import com.chatwaifu.mobile.ui.ChannelListBean
import com.chatwaifu.mobile.ui.dp
import java.io.File

class ChannelListFragment : Fragment() {

    lateinit var binding: FragmentChannelListBinding
    private val activityViewModel: ChatActivityViewModel by activityViewModels()
    private val listAdapter = ChannelListAdapter()
    private val sp: SharedPreferences by lazy {
        requireActivity().getSharedPreferences(Constant.SAVED_STORE, Context.MODE_PRIVATE)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentChannelListBinding.inflate(inflater)
        listAdapter.onClick = ::onClick
        binding.channelList.adapter = listAdapter
        binding.channelList.layoutManager = LinearLayoutManager(requireContext())
        binding.channelList.addItemDecoration(object :ItemDecoration(){
            override fun getItemOffsets(outRect: Rect, itemPosition: Int, parent: RecyclerView) {
                outRect.set(0,0,0,2.dp)
            }
        })
        binding.fabButton.setOnClickListener {
            onFabClick()
        }
        activityViewModel.loadVITSModelLiveData.value = false
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initObserver()
        activityViewModel.initModel(requireContext())
        if (sp.getBoolean(SAVED_FLAG_NEED_COPY_DATA, true)) {
            sp.edit().putBoolean(SAVED_FLAG_NEED_COPY_DATA, false).apply()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun initObserver() {
        activityViewModel.initModelResultLiveData.observe(viewLifecycleOwner){ models ->
            listAdapter.items.clear()
            listAdapter.items.addAll(models)
            listAdapter.notifyDataSetChanged()
        }
        activityViewModel.loadVITSModelLiveData.observe(viewLifecycleOwner){ success ->
            if (success) {
                (requireActivity() as? ChatActivity)?.showChatFragment()
            }
        }
        activityViewModel.loadingUILiveData.observe(viewLifecycleOwner){show ->
            showLoading(show.first, show.second)
        }
    }

    private fun showLoading(show: Boolean, text: String = "") {
        binding.loadingGroup.visibility = if (show) View.VISIBLE else View.GONE
        binding.loadingText.text = text
    }

    private fun onClick(item: ChannelListBean) {
        activityViewModel.currentLive2DModelPath = item.characterPath
        activityViewModel.currentLive2DModelName = item.characterName
        if (item.characterName == activityViewModel.currentVITSModelName) {
            (requireActivity() as? ChatActivity)?.showChatFragment()
            return
        }
        activityViewModel.currentVITSModelName = item.characterName
        val vitsDir = item.characterVitsPath
        activityViewModel.loadVitsModel(File(vitsDir).listFiles()?.toList())
    }

    private fun onFabClick() {
        activityViewModel.initModel(requireContext())
    }
}