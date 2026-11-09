package app.locana.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream

/** Turns a picked image into a small square JPEG (centre crop, 384 px) that is cheap to store and send. */
object PhotoPrep {
    const val SIZE = 384

    fun prepare(context: Context, uri: Uri): ByteArray? = runCatching {
        val source = if (Build.VERSION.SDK_INT >= 28) {
            // ImageDecoder applies the photo's rotation for us.
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, _, _ -> d.isMutableRequired = false; d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }
        } else {
            context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
        }
        square(source).let { out ->
            ByteArrayOutputStream().also { out.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
        }
    }.getOrNull()

    fun square(src: Bitmap, size: Int = SIZE): Bitmap {
        val side = minOf(src.width, src.height)
        val x = (src.width - side) / 2
        val y = (src.height - side) / 2
        val cropped = Bitmap.createBitmap(src, x, y, side, side)
        return if (side == size) cropped else Bitmap.createScaledBitmap(cropped, size, size, true)
    }
}
