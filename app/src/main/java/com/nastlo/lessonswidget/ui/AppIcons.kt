package com.nastlo.lessonswidget.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Иконки, которых нет в наборе material-icons-core. */
object AppIcons {

    /** Шестерёнка «Настройки» (материальный path, 24dp). */
    val Settings: ImageVector by lazy {
        ImageVector.Builder(
            name = "Settings",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = addPathNodes(
                    "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 " +
                        "-0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32" +
                        "c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 " +
                        "-1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84" +
                        "c-0.24,0 -0.44,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29" +
                        "L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 " +
                        "2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94" +
                        "l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 " +
                        "0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 " +
                        "0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54" +
                        "c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22" +
                        "l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6" +
                        "c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6" +
                        "S13.98,15.6 12,15.6z",
                ),
                fill = SolidColor(Color.White),
            )
        }.build()
    }

    /** «?» в кружочке — частые вопросы (штриховыми линиями, 24dp). */
    val Help: ImageVector by lazy {
        ImageVector.Builder(
            name = "Help",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            // кольцо
            addPath(
                pathData = addPathNodes("M3.5,12a8.5,8.5 0 1,1 17,0a8.5,8.5 0 1,1 -17,0z"),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.7f,
                strokeLineCap = StrokeCap.Round,
            )
            // знак вопроса: дуга сверху + вертикальный хвостик
            addPath(
                pathData = addPathNodes(
                    "M9.7,9.3c0,-1.3 1.1,-2 2.3,-2c1.2,0 2.3,0.7 2.3,2c0,1 -0.6,1.4 -1.2,1.9" +
                        "c-0.5,0.4 -1,0.8 -1,1.6l0,0.7",
                ),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.7f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            // точка
            addPath(
                pathData = addPathNodes("M11.2,16.9a0.95,0.95 0 1,0 1.9,0a0.95,0.95 0 1,0 -1.9,0z"),
                fill = SolidColor(Color.White),
            )
        }.build()
    }
}
