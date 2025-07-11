package org.fordem.indifi.ui.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import org.fordem.indifi.ui.model.Message

class MessageAdapter(context: Context, messages: List<Message>) :
    ArrayAdapter<Message>(context, 0, messages) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val message = getItem(position)
        val layoutId = if (message!!.isSentByMe) {
            android.R.layout.simple_list_item_1
        } else {
            android.R.layout.simple_list_item_2
        }

        val view = LayoutInflater.from(context).inflate(layoutId, parent, false)
        val textView = view.findViewById<TextView>(android.R.id.text1)
        textView.text = message.content
        return view
    }
}
