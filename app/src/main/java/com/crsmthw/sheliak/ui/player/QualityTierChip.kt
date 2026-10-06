package com.crsmthw.sheliak.ui.player

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.domain.AudioFormatInfo
import com.crsmthw.sheliak.domain.QualityClassifier
import com.crsmthw.sheliak.domain.QualityTier

/**
 * The quality chip, centred under the seek bar (DESIGN §3): the tier and the exact values it was decided from —
 * "Hi-Res Lossless · FLAC 24-bit / 96 kHz", "High Quality · AAC 256 kbps". [format] is the live decoder format
 * when known (the truth for a transcode), else the track's indexed one; nothing shows without either.
 */
@Composable
fun QualityTierChip(
    format  : AudioFormatInfo?,
    tint    : Color,
    modifier: Modifier = Modifier,
) {
    if (format == null) return
    val tier   = stringResource(QualityClassifier.classify(format).labelRes())
    val detail = qualityDetailText(qualityDetailOf(format))
    Surface(
        modifier     = modifier,
        shape        = CircleShape,
        color        = tint.copy(alpha = 0.12f),
        contentColor = tint,
        border       = BorderStroke(1.dp, tint.copy(alpha = 0.4f)),
    ) {
        Text(
            text     = stringResource(R.string.quality_chip, tier, detail),
            style    = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** The tier's name. */
@StringRes
fun QualityTier.labelRes(): Int = when (this) {
    QualityTier.HI_RES_LOSSLESS -> R.string.quality_hi_res_lossless
    QualityTier.LOSSLESS        -> R.string.quality_lossless
    QualityTier.HIGH_QUALITY    -> R.string.quality_high_quality
    QualityTier.HIGH_EFFICIENCY -> R.string.quality_high_efficiency
}

/** [detail] in words: each variant has its own template, so the units live in string resources. */
@Composable
@ReadOnlyComposable
private fun qualityDetailText(detail: QualityDetail): String = when (detail) {
    is QualityDetail.DepthAndRate -> stringResource(R.string.quality_detail_depth_rate, detail.codec, detail.bitDepth, detail.khz)
    is QualityDetail.Rate         -> stringResource(R.string.quality_detail_rate, detail.codec, detail.khz)
    is QualityDetail.Depth        -> stringResource(R.string.quality_detail_depth, detail.codec, detail.bitDepth)
    is QualityDetail.Bitrate      -> stringResource(R.string.quality_detail_bitrate, detail.codec, detail.kbps)
    is QualityDetail.CodecOnly    -> detail.codec
}
