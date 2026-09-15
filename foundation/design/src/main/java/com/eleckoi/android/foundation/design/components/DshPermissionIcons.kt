package com.eleckoi.android.foundation.design.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Permission glyph vectors for the permission selector. */
object DshPermissionIcons {
    val ReadOnly: ImageVector by lazy {
        permissionIcon("DshPermissionReadOnly") {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.31831f,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(8.20554f, 0.899994f)
                lineTo(14.7901f, 3.36857f)
                lineTo(14.7901f, 7.01026f)
                curveTo(14.7901f, 12f, 11.0466f, 14.2103f, 8.20554f, 15.3f)
                curveTo(5.36446f, 14.2103f, 1.62012f, 12f, 1.62012f, 7.01026f)
                lineTo(1.62012f, 3.36857f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(12.1654f, 5.7552f)
                lineTo(8.9447f, 9.41475f)
                curveTo(8.73044f, 9.65816f, 8.53628f, 9.8804f, 8.35774f, 10.0423f)
                curveTo(8.1713f, 10.2114f, 7.94235f, 10.3717f, 7.64016f, 10.4254f)
                curveTo(7.48207f, 10.4535f, 7.32f, 10.4552f, 7.16151f, 10.4294f)
                curveTo(6.85843f, 10.3801f, 6.62728f, 10.2223f, 6.43836f, 10.0559f)
                curveTo(6.25752f, 9.89653f, 6.06037f, 9.67732f, 5.84264f, 9.43705f)
                lineTo(4.72925f, 8.20897f)
                lineTo(5.63557f, 7.38707f)
                lineTo(6.74897f, 8.61594f)
                curveTo(6.98603f, 8.87755f, 7.12974f, 9.03533f, 7.24673f, 9.13839f)
                curveTo(7.31033f, 9.19443f, 7.34485f, 9.21476f, 7.35823f, 9.22122f)
                curveTo(7.38068f, 9.22484f, 7.40352f, 9.22515f, 7.42593f, 9.22122f)
                curveTo(7.40522f, 9.22502f, 7.42893f, 9.23294f, 7.53583f, 9.136f)
                curveTo(7.65132f, 9.03126f, 7.79316f, 8.87139f, 8.02643f, 8.60638f)
                lineTo(11.2479f, 4.94763f)
                close()
            }
        }
    }

    val WorkspaceWrite: ImageVector by lazy {
        permissionIcon("DshPermissionWorkspaceWrite") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(8.08887f, 0.251709f)
                curveTo(8.20479f, 0.23085f, 8.32486f, 0.241168f, 8.43652f, 0.282959f)
                lineTo(15.0215f, 2.75171f)
                curveTo(15.2787f, 2.84819f, 15.4492f, 3.09414f, 15.4492f, 3.3689f)
                lineTo(15.4492f, 7.0105f)
                curveTo(15.4492f, 7.10986f, 15.4441f, 7.2081f, 15.4414f, 7.30542f)
                curveTo(15.0285f, 7.07175f, 14.5905f, 6.87695f, 14.1309f, 6.73022f)
                lineTo(14.1309f, 3.82495f)
                lineTo(8.20508f, 1.60327f)
                lineTo(2.2793f, 3.82495f)
                lineTo(2.2793f, 7.0105f)
                curveTo(2.27936f, 9.7171f, 3.4745f, 11.5379f, 5.02734f, 12.7947f)
                curveTo(5.01025f, 12.9942f, 5f, 13.1962f, 5f, 13.4001f)
                curveTo(5.00001f, 13.7617f, 5.02722f, 14.1169f, 5.08008f, 14.4636f)
                curveTo(2.91555f, 13.0393f, 0.961014f, 10.752f, 0.960938f, 7.0105f)
                lineTo(0.960938f, 3.3689f)
                curveTo(0.960938f, 3.09417f, 1.13146f, 2.84821f, 1.38867f, 2.75171f)
                lineTo(7.97461f, 0.282959f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(11.3525f, 5.64688f)
                lineTo(11.3525f, 6.85688f)
                lineTo(5f, 6.85688f)
                lineTo(5f, 5.64688f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(9.5824f, 8.29376f)
                lineTo(9.5824f, 9.50376f)
                lineTo(5f, 9.50376f)
                lineTo(5f, 8.29376f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(14.6647f, 15.6852f)
                lineTo(10.0338f, 15.6852f)
                lineTo(11.5511f, 14.3547f)
                lineTo(14.6647f, 14.3547f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(8.14852f, 14.1308f)
                lineTo(7.33925f, 15.4976f)
                curveTo(7.22458f, 15.6912f, 7.42245f, 15.9194f, 7.63037f, 15.8333f)
                lineTo(9.09785f, 15.2254f)
                lineTo(15.0399f, 10.0719f)
                lineTo(14.0905f, 8.97733f)
                close()
            }
        }
    }

    val FullAccess: ImageVector by lazy {
        permissionIcon("DshPermissionFullAccess") {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.31831f,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(8.20554f, 0.899994f)
                lineTo(14.7901f, 3.36857f)
                lineTo(14.7901f, 7.01026f)
                curveTo(14.7901f, 12f, 11.0466f, 14.2103f, 8.20554f, 15.3f)
                curveTo(5.36446f, 14.2103f, 1.62012f, 12f, 1.62012f, 7.01026f)
                lineTo(1.62012f, 3.36857f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(9.10094f, 4.5f)
                lineTo(9.10094f, 8.75939f)
                lineTo(7.59888f, 8.75939f)
                lineTo(7.59888f, 4.5f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(9.10094f, 9.8114f)
                lineTo(9.10094f, 11.5f)
                lineTo(7.59888f, 11.5f)
                lineTo(7.59888f, 9.8114f)
                close()
            }
        }
    }

    private inline fun permissionIcon(
        name: String,
        block: ImageVector.Builder.() -> Unit,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 16.dp,
        defaultHeight = 16.dp,
        viewportWidth = 16f,
        viewportHeight = 16f,
    ).apply(block).build()
}
