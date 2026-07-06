package dev.feature.auth.presentation.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Lucide ikonalari — dizayndagi aynan SVG `d` yo'llari [PathParser] orqali
 * [ImageVector] ga aylantirilgan. Chiziqli (stroke) ikonalar `Icon(tint=...)` bilan
 * bo'yaladi; ko'p rangli logolar `Image` bilan chiziladi.
 */
private fun strokeIcon(name: String, vararg paths: String, viewport: Float = 24f): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = viewport,
        viewportHeight = viewport,
    ).apply {
        paths.forEach { d ->
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

object AuthIcons {
    val GraduationCap = strokeIcon(
        "GraduationCap",
        "M22 10 12 5 2 10l10 5 10-5Z",
        "M6 12v5c0 1 2 3 6 3s6-2 6-3v-5",
    )
    val Phone = strokeIcon(
        "Phone",
        "M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.13.96.36 1.9.7 2.81a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.91.34 1.85.57 2.81.7A2 2 0 0 1 22 16.92Z",
    )
    val Mail = strokeIcon(
        "Mail",
        "M2 6a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2z",
        "m22 7-10 5L2 7",
    )
    val Lock = strokeIcon(
        "Lock",
        "M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2z",
        "M7 11V7a5 5 0 0 1 10 0v4",
    )
    val Eye = strokeIcon(
        "Eye",
        "M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z",
        "M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0z",
    )
    val EyeOff = strokeIcon(
        "EyeOff",
        "M9.88 9.88a3 3 0 0 0 4.24 4.24",
        "M10.73 5.08A10.43 10.43 0 0 1 12 5c7 0 10 7 10 7a13.16 13.16 0 0 1-1.67 2.68",
        "M6.61 6.61A13.526 13.526 0 0 0 2 12s3 7 10 7a9.74 9.74 0 0 0 5.39-1.61",
        "M2 2l20 20",
    )
    val ArrowRight = strokeIcon("ArrowRight", "M5 12h14", "m12 5 7 7-7 7")
    val ArrowLeft = strokeIcon("ArrowLeft", "M19 12H5", "m12 19-7-7 7-7")
    val Check = strokeIcon("Check", "M20 6 9 17l-5-5")
    val Clock = strokeIcon(
        "Clock",
        "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z",
        "M12 6v6l4 2",
    )
    val Calendar = strokeIcon(
        "Calendar",
        "M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z",
        "M16 2v4",
        "M8 2v4",
        "M3 10h18",
    )
    val Search = strokeIcon(
        "Search",
        "M11 3a8 8 0 1 0 0 16 8 8 0 0 0 0-16z",
        "m21 21-4.3-4.3",
    )
    val ShieldCheck = strokeIcon(
        "ShieldCheck",
        "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z",
        "m9 12 2 2 4-4",
    )
    val ChevronDown = strokeIcon("ChevronDown", "m6 9 6 6 6-6")
    val Close = strokeIcon("Close", "M18 6 6 18", "M6 6l12 12")
    val MessageSquare = strokeIcon(
        "MessageSquare",
        "M7 8h10",
        "M7 12h6",
        "M4 4h16a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1h-6l-4 4v-4H4a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1Z",
    )
    val ScanFace = strokeIcon(
        "ScanFace",
        "M4 8V6a2 2 0 0 1 2-2h2",
        "M4 16v2a2 2 0 0 0 2 2h2",
        "M16 4h2a2 2 0 0 1 2 2v2",
        "M16 20h2a2 2 0 0 0 2-2v-2",
        "M8 14s1.5 2 4 2 4-2 4-2",
        "M9 9h.01",
        "M15 9h.01",
    )
    val Briefcase = strokeIcon(
        "Briefcase",
        "M4 7h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2z",
        "M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16",
    )
    val Store = strokeIcon(
        "Store",
        "M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4Z",
        "M3 6h18",
        "M16 10a4 4 0 0 1-8 0",
    )
    val Building = strokeIcon(
        "Building",
        "M6 22V4a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v18",
        "M2 22h20",
        "M10 7h4",
        "M10 11h4",
        "M10 15h4",
    )

    /** Apple logo — bitta to'ldirilgan yo'l; `Icon(tint=...)` bilan bo'yaladi. */
    val Apple: ImageVector = ImageVector.Builder(
        "Apple", 24.dp, 24.dp, 24f, 24f,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString(
                "M17.05 12.04c-.03-2.94 2.4-4.35 2.51-4.42-1.37-2-3.5-2.28-4.26-2.31-1.81-.18-3.53 1.07-4.45 1.07-.92 0-2.33-1.04-3.83-1.01-1.97.03-3.79 1.15-4.8 2.91-2.05 3.55-.52 8.8 1.47 11.68.97 1.41 2.13 2.99 3.64 2.93 1.46-.06 2.01-.94 3.78-.94 1.76 0 2.26.94 3.8.91 1.57-.03 2.56-1.43 3.52-2.85 1.11-1.63 1.57-3.21 1.59-3.29-.03-.02-3.05-1.17-3.08-4.66zM14.13 3.57c.81-.98 1.36-2.34 1.21-3.7-1.17.05-2.59.78-3.43 1.76-.75.87-1.41 2.26-1.23 3.59 1.3.1 2.64-.66 3.45-1.65z",
            ).toNodes(),
            fill = SolidColor(Color.Black),
        )
    }.build()

    /** Google 'G' — ko'p rangli, `Image` bilan chiziladi. */
    val Google: ImageVector = ImageVector.Builder(
        "Google", 24.dp, 24.dp, 48f, 48f,
    ).apply {
        addPath(
            PathParser().parsePathString("M43.6 20.1H42V20H24v8h11.3C33.7 32.7 29.3 36 24 36c-6.6 0-12-5.4-12-12s5.4-12 12-12c3.1 0 5.8 1.2 7.9 3l5.7-5.7C34 6.1 29.3 4 24 4 13 4 4 13 4 24s9 20 20 20 20-9 20-20c0-1.3-.1-2.7-.4-3.9z").toNodes(),
            fill = SolidColor(Color(0xFFFFC107)),
        )
        addPath(
            PathParser().parsePathString("m6.3 14.7 6.6 4.8C14.7 15.1 19 12 24 12c3.1 0 5.8 1.2 7.9 3l5.7-5.7C34 6.1 29.3 4 24 4 16.3 4 9.7 8.3 6.3 14.7z").toNodes(),
            fill = SolidColor(Color(0xFFFF3D00)),
        )
        addPath(
            PathParser().parsePathString("M24 44c5.2 0 9.9-2 13.4-5.2l-6.2-5.2C29.2 35.1 26.7 36 24 36c-5.2 0-9.6-3.3-11.3-7.9l-6.5 5C9.5 39.6 16.2 44 24 44z").toNodes(),
            fill = SolidColor(Color(0xFF4CAF50)),
        )
        addPath(
            PathParser().parsePathString("M43.6 20.1H42V20H24v8h11.3c-.8 2.2-2.2 4.2-4.1 5.6l6.2 5.2C36.9 40.2 44 34 44 24c0-1.3-.1-2.7-.4-3.9z").toNodes(),
            fill = SolidColor(Color(0xFF1976D2)),
        )
    }.build()

    /** Telegram — ko'k doira + oq samolyot, `Image` bilan chiziladi. */
    val Telegram: ImageVector = ImageVector.Builder(
        "Telegram", 24.dp, 24.dp, 24f, 24f,
    ).apply {
        addPath(
            PathParser().parsePathString("M12 1a11 11 0 1 0 0 22 11 11 0 0 0 0-22z").toNodes(),
            fill = SolidColor(Color(0xFF229ED9)),
        )
        addPath(
            PathParser().parsePathString("M5.6 11.7l11-4.24c.51-.19.96.12.79.9l-1.87 8.82c-.13.6-.5.75-1 .47l-2.76-2.03-1.33 1.28c-.15.15-.27.27-.55.27l.2-2.8 5.12-4.62c.22-.2-.05-.31-.34-.11L9.9 13.5l-2.73-.85c-.59-.19-.6-.59.13-.87z").toNodes(),
            fill = SolidColor(Color.White),
        )
    }.build()
}
