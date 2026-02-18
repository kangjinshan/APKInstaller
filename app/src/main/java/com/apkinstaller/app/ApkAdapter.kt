package com.apkinstaller.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ApkAdapter(
    private val apkList: MutableList<ApkInfo>,
    private val onApkClick: (ApkInfo) -> Unit
) : RecyclerView.Adapter<ApkAdapter.ApkViewHolder>() {

    private var selectedPosition = -1

    inner class ApkViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvAppName: TextView = itemView.findViewById(R.id.tvAppName)
        val tvPackageName: TextView = itemView.findViewById(R.id.tvPackageName)
        val tvVersionInfo: TextView = itemView.findViewById(R.id.tvVersionInfo)
        val tvFileSize: TextView = itemView.findViewById(R.id.tvFileSize)
        val tvFilePath: TextView = itemView.findViewById(R.id.tvFilePath)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    // 取消之前的选择
                    if (selectedPosition >= 0 && selectedPosition < apkList.size) {
                        apkList[selectedPosition].isSelected = false
                        notifyItemChanged(selectedPosition)
                    }
                    
                    // 选择新APK
                    selectedPosition = position
                    apkList[position].isSelected = true
                    notifyItemChanged(position)
                    
                    onApkClick(apkList[position])
                }
            }
        }

        fun bind(apkInfo: ApkInfo) {
            tvAppName.text = apkInfo.displayName
            
            // 包名
            if (apkInfo.packageName != null) {
                tvPackageName.text = apkInfo.packageName
                tvPackageName.visibility = View.VISIBLE
            } else {
                tvPackageName.visibility = View.GONE
            }
            
            // 版本信息
            if (apkInfo.versionName != null && apkInfo.versionCode != null) {
                tvVersionInfo.text = "v${apkInfo.versionName} (${apkInfo.versionCode})"
                tvVersionInfo.visibility = View.VISIBLE
            } else if (apkInfo.versionName != null) {
                tvVersionInfo.text = "v${apkInfo.versionName}"
                tvVersionInfo.visibility = View.VISIBLE
            } else {
                tvVersionInfo.visibility = View.GONE
            }
            
            // 文件大小
            tvFileSize.text = apkInfo.fileSizeFormatted
            
            // 文件路径
            tvFilePath.text = apkInfo.filePath
            
            // 设置选中状态的背景色
            if (apkInfo.isSelected) {
                itemView.setBackgroundColor(itemView.context.getColor(R.color.teal_200))
            } else {
                itemView.setBackgroundColor(itemView.context.getColor(android.R.color.transparent))
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ApkViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_apk, parent, false)
        return ApkViewHolder(view)
    }

    override fun onBindViewHolder(holder: ApkViewHolder, position: Int) {
        holder.bind(apkList[position])
    }

    override fun getItemCount() = apkList.size

    fun updateApkList(newApkList: List<ApkInfo>) {
        apkList.clear()
        apkList.addAll(newApkList)
        selectedPosition = -1
        notifyDataSetChanged()
    }
    
    fun addApk(apkInfo: ApkInfo) {
        apkList.add(apkInfo)
        notifyItemInserted(apkList.size - 1)
    }
}
