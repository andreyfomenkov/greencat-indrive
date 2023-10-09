package ru.fomenkov.analytics

import com.mixpanel.mixpanelapi.ClientDelivery
import com.mixpanel.mixpanelapi.MessageBuilder
import com.mixpanel.mixpanelapi.MixpanelAPI
import org.json.JSONObject
import ru.fomenkov.utils.Log
import ru.fomenkov.utils.distinctId
import java.io.IOException

object Analytics {

    private const val PROJECT_TOKEN = "06f7c0e9f6ada60a5fe3b8a21595a955"
    private val messageBuilder = MessageBuilder(PROJECT_TOKEN)
    private val mixpanel = MixpanelAPI()

    fun launch() {
        deliver(Event.Launch)
    }

    fun complete(duration: Long) {
        deliver(Event.Complete, Key.Duration to duration)
    }

    fun failed(message: String) {
        deliver(Event.Failed, Key.Message to message)
    }

    fun drop(message: String) {
        deliver(Event.Drop, Key.Message to message)
    }

    private sealed class Event(val name: String) {
        data object Launch : Event("launch")
        data object Complete : Event("complete")
        data object Failed : Event("failed")
        data object Drop : Event("drop")
    }

    private sealed class Key(val name: String) {
        data object Duration : Key("duration")
        data object Message : Key("message")
    }

    private fun deliver(event: Event, vararg props: Pair<Key, Any>) {
        try {
            val json = if (props.isEmpty()) {
                null
            } else {
                JSONObject().apply {
                    props.forEach { (key, value) -> put(key.name, value) }
                }
            }
            val delivery = ClientDelivery()
            val message = messageBuilder.event(distinctId, event.name, json)
            delivery.addMessage(message)
            mixpanel.deliver(delivery)
            Log.d("Analytics: $message")

        } catch (error: IOException) {
            Log.d("Failed to deliver event: ${error.message}")
        }
    }
}