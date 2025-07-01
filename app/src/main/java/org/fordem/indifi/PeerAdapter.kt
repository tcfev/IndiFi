package org.fordem.indifi

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView

class PeerAdapter(
    context: Context,
    private var peers: MutableList<PeerDevice>,
    private val onActionClick: (PeerDevice) -> Unit,
) : ArrayAdapter<PeerDevice>(context, 0, peers) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_peer, parent, false)

        val nameTextView = view.findViewById<TextView>(R.id.deviceNameTextView)
        val chatButton = view.findViewById<Button>(R.id.startChatButton)

        val peer = peers[position]
        nameTextView.text = peer.name
        chatButton.text = if (peer.mode == PeerActionMode.CONNECT) "Connect" else "Chat"

        chatButton.setOnClickListener {
            onActionClick(peer)
        }

        nameTextView.setOnClickListener {
            Constants.peerPositionCallback(peer, position)
        }

        return view
    }

    fun setData(newPeers: MutableList<PeerDevice>) {
        peers.clear()
        peers.addAll(newPeers)
        notifyDataSetChanged()
    }
}
