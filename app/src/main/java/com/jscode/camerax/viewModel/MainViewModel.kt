package com.jscode.camerax.viewModel

import android.view.View
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.jscode.camerax.util.viewFadeIn
import com.jscode.camerax.util.viewFadeOut

class MainViewModel : ViewModel() {
    private val _recognitionList = MutableLiveData<List<Recognition>>()
    val recognitionList: LiveData<List<Recognition>> = _recognitionList

    fun updateData(recognitions: List<Recognition>){
        _recognitionList.postValue(recognitions)
    }
    fun fade(v1:View,v2:View){
        viewFadeIn(v2)
        viewFadeOut(v1)
    }
}

data class Recognition(val label:String, val confidence:Float) {
    val probabilityString = String.format("%.1f%%", confidence * 100.0f)
}