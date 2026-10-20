package id.abati.perjadin.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import id.abati.perjadin.MainActivity
import id.abati.perjadin.R
import id.abati.perjadin.data.Repository
import id.abati.perjadin.util.Fmt

/** Receiver widget layar utama; semua logika ada di [TripWidget]. */
class TripWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = TripWidget.render(context)
        ids.forEach { manager.updateAppWidget(it, views) }
    }
}

/**
 * Widget ringkasan perjalanan aktif + tombol "Catat Pengeluaran".
 * Dibangun dengan RemoteViews biasa (tanpa library tambahan).
 */
object TripWidget {
    const val ACTION_QUICK_EXPENSE = "id.abati.perjadin.QUICK_EXPENSE"
    const val ACTION_OPEN_TRIP = "id.abati.perjadin.OPEN_TRIP"
    const val ACTION_NEW_TRIP = "id.abati.perjadin.NEW_TRIP"
    const val EXTRA_TRIP_ID = "trip_id"

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, TripWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val views = render(context)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    private fun intent(context: Context, action: String, tripId: String?, requestCode: Int): PendingIntent {
        val i = Intent(context, MainActivity::class.java).apply {
            this.action = action
            if (tripId != null) putExtra(EXTRA_TRIP_ID, tripId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(context, requestCode, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun render(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_trip)
        val data = Repository.get(context).state.value
        val trip = data.focusTrip(Fmt.today())

        if (trip == null) {
            views.setTextViewText(R.id.widget_odr, "Perjadin")
            views.setTextViewText(R.id.widget_title, "Tidak ada perjalanan aktif")
            views.setTextViewText(R.id.widget_amount, "Buat perjalanan baru dari nomor ODR Anda")
            views.setViewVisibility(R.id.widget_progress, View.GONE)
            views.setViewVisibility(R.id.widget_progress_over, View.GONE)
            views.setTextViewText(R.id.widget_button, "+ Perjalanan Baru")
            views.setOnClickPendingIntent(R.id.widget_button, intent(context, ACTION_NEW_TRIP, null, 3))
            views.setOnClickPendingIntent(R.id.widget_root, intent(context, Intent.ACTION_MAIN, null, 4))
            return views
        }

        val over = trip.remaining < 0
        views.setTextViewText(R.id.widget_odr, listOf(trip.odrNumber, trip.destination).filter { it.isNotBlank() }.joinToString(" · "))
        views.setTextViewText(R.id.widget_title, trip.purpose)
        views.setTextViewText(
            R.id.widget_amount,
            if (trip.totalBudget <= 0) "Realisasi ${Fmt.rupiah(trip.totalActual)} · anggaran belum diisi"
            else if (over) "Melebihi ${Fmt.rupiah(-trip.remaining)} dari ${Fmt.rupiah(trip.totalBudget)}"
            else "Sisa ${Fmt.rupiah(trip.remaining)} dari ${Fmt.rupiah(trip.totalBudget)}",
        )
        // Dua ProgressBar (normal & merah) karena warna tint tidak bisa diubah lewat RemoteViews di Android < 12
        val percent = (trip.usage * 100).toInt().coerceIn(0, 100)
        views.setViewVisibility(R.id.widget_progress, if (over) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_progress_over, if (over) View.VISIBLE else View.GONE)
        views.setProgressBar(R.id.widget_progress, 100, percent, false)
        views.setProgressBar(R.id.widget_progress_over, 100, 100, false)
        views.setTextViewText(R.id.widget_button, "+ Catat Pengeluaran")
        views.setOnClickPendingIntent(R.id.widget_button, intent(context, ACTION_QUICK_EXPENSE, trip.id, 1))
        views.setOnClickPendingIntent(R.id.widget_root, intent(context, ACTION_OPEN_TRIP, trip.id, 2))
        return views
    }
}
