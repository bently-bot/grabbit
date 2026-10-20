package com.bentlybro.grabbit.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** The few icons material-icons-core lacks, drawn as 24dp stroked vectors. */
object GrabbitIcons {

    private fun stroked(name: String, block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        ).build()

    val Download: ImageVector by lazy {
        stroked("Download") {
            moveTo(12f, 3f); lineTo(12f, 15f)
            moveTo(7f, 10f); lineTo(12f, 15f); lineTo(17f, 10f)
            moveTo(4f, 17f); lineTo(4f, 20f); lineTo(20f, 20f); lineTo(20f, 17f)
        }
    }

    val Paste: ImageVector by lazy {
        stroked("Paste") {
            moveTo(9f, 4f); lineTo(6f, 4f)
            curveTo(5f, 4f, 4f, 5f, 4f, 6f); lineTo(4f, 20f)
            curveTo(4f, 21f, 5f, 22f, 6f, 22f); lineTo(18f, 22f)
            curveTo(19f, 22f, 20f, 21f, 20f, 20f); lineTo(20f, 6f)
            curveTo(20f, 5f, 19f, 4f, 18f, 4f); lineTo(15f, 4f)
            moveTo(9f, 2f); lineTo(15f, 2f); lineTo(15f, 6f); lineTo(9f, 6f); close()
        }
    }

    val Link: ImageVector by lazy {
        stroked("Link") {
            moveTo(10f, 13f)
            curveTo(11.5f, 15f, 14.5f, 15.2f, 16.2f, 13.5f); lineTo(19f, 10.7f)
            curveTo(20.8f, 8.9f, 20.8f, 6f, 19f, 4.2f)
            curveTo(17.2f, 2.4f, 14.3f, 2.4f, 12.5f, 4.2f); lineTo(11f, 5.7f)
            moveTo(14f, 11f)
            curveTo(12.5f, 9f, 9.5f, 8.8f, 7.8f, 10.5f); lineTo(5f, 13.3f)
            curveTo(3.2f, 15.1f, 3.2f, 18f, 5f, 19.8f)
            curveTo(6.8f, 21.6f, 9.7f, 21.6f, 11.5f, 19.8f); lineTo(13f, 18.3f)
        }
    }

    val Music: ImageVector by lazy {
        stroked("Music") {
            moveTo(9f, 18f); lineTo(9f, 5f); lineTo(21f, 3f); lineTo(21f, 16f)
            moveTo(9f, 18f); arcToRelative(3f, 3f, 0f, true, true, -6f, 0f); arcToRelative(3f, 3f, 0f, true, true, 6f, 0f)
            moveTo(21f, 16f); arcToRelative(3f, 3f, 0f, true, true, -6f, 0f); arcToRelative(3f, 3f, 0f, true, true, 6f, 0f)
        }
    }

    val Retry: ImageVector by lazy {
        stroked("Retry") {
            moveTo(3f, 12f)
            arcToRelative(9f, 9f, 0f, true, false, 3f, -6.7f)
            moveTo(3f, 4f); lineTo(3f, 9f); lineTo(8f, 9f)
        }
    }
}
