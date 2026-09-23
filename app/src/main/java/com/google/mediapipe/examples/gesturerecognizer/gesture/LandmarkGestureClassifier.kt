package com.google.mediapipe.examples.gesturerecognizer.gesture

import java.util.Locale
import kotlin.math.acos
import kotlin.math.sqrt

data class GestureLandmark3D(val x: Float, val y: Float, val z: Float)

data class FingerExtensionScores(val thumb: Float, val index: Float, val middle: Float, val ring: Float, val pinky: Float) {
    init {
        require(listOf(thumb, index, middle, ring, pinky).all { score -> score.isFinite() && score in 0f..1f }) {
            "Finger extension scores must be finite and between 0 and 1."
        }
    }

    val summary: String
        get() = String.format(
            Locale.US,
            "thumb %.2f, index %.2f, middle %.2f, ring %.2f, pinky %.2f",
            thumb,
            index,
            middle,
            ring,
            pinky
        )
}

data class LandmarkGestureEstimate(val resolvedName: String?, val score: Float, val margin: Float, val fingers: FingerExtensionScores) {
    init {
        require(resolvedName == null || resolvedName.isNotBlank()) { "Resolved gesture name must not be blank." }
        require(score.isFinite() && score in 0f..1f) { "Gesture score must be finite and between 0 and 1." }
        require(margin.isFinite() && margin in 0f..1f) { "Gesture margin must be finite and between 0 and 1." }
    }
}

object LandmarkGestureClassifier {
    fun estimate(landmarks: List<GestureLandmark3D>): LandmarkGestureEstimate? {
        if (landmarks.size != LANDMARK_COUNT || landmarks.any { landmark -> !landmark.hasFiniteCoordinates() }) {
            return null
        }

        val points = landmarks.map { landmark ->
            Vector3(landmark.x.toDouble(), landmark.y.toDouble(), landmark.z.toDouble())
        }
        return palmAxes(points)?.let { axes ->
            extensionScores(points, axes)?.let(::resolveEstimate)
        }
    }

    private fun extensionScores(points: List<Vector3>, palmAxes: PalmAxes): FingerExtensionScores? {
        val scores = listOf(
            thumbExtension(points, palmAxes),
            fingerExtension(points, INDEX_FINGER, palmAxes),
            fingerExtension(points, MIDDLE_FINGER, palmAxes),
            fingerExtension(points, RING_FINGER, palmAxes),
            fingerExtension(points, PINKY_FINGER, palmAxes)
        )
        return if (scores.any { score -> score == null }) {
            null
        } else {
            FingerExtensionScores(
                thumb = checkNotNull(scores[0]).toFloat(),
                index = checkNotNull(scores[1]).toFloat(),
                middle = checkNotNull(scores[2]).toFloat(),
                ring = checkNotNull(scores[3]).toFloat(),
                pinky = checkNotNull(scores[4]).toFloat()
            )
        }
    }

    private fun resolveEstimate(fingers: FingerExtensionScores): LandmarkGestureEstimate {
        val candidates = GESTURE_TEMPLATES
            .map { template -> GestureCandidateScore(template, template.score(fingers)) }
            .sortedByDescending { candidate -> candidate.score }
        val best = candidates.first()
        val margin = (best.score - candidates[1].score).coerceIn(0.0, 1.0)
        val resolvedName = best.template.name.takeIf {
            best.score >= MIN_GESTURE_SCORE &&
                margin >= MIN_GESTURE_MARGIN &&
                best.template.matchesRequiredFingerStates(fingers)
        }

        return LandmarkGestureEstimate(
            resolvedName = resolvedName,
            score = best.score.toFloat(),
            margin = margin.toFloat(),
            fingers = fingers
        )
    }

    private fun palmAxes(points: List<Vector3>): PalmAxes? {
        val wrist = points[WRIST]
        val radialVector = points[INDEX_MCP] - points[PINKY_MCP]
        val forwardVector = points[MIDDLE_MCP] - wrist
        val palmScale = (radialVector.length() + forwardVector.length()) / 2.0
        return if (!palmScale.isFinite() || palmScale <= MIN_ABSOLUTE_SCALE) {
            null
        } else {
            val radialAxis = radialVector.normalizedOrNull(palmScale)
            val orthogonalForward = radialAxis?.let { axis -> forwardVector - axis * forwardVector.dot(axis) }
            val forwardAxis = orthogonalForward?.normalizedOrNull(palmScale)
            if (radialAxis == null || orthogonalForward == null || forwardAxis == null) {
                null
            } else if (orthogonalForward.length() < palmScale * MIN_PALM_AXIS_RATIO ||
                radialAxis.cross(forwardAxis).length() < MIN_ORTHOGONAL_AXIS_LENGTH
            ) {
                null
            } else {
                PalmAxes(radial = radialAxis, forward = forwardAxis, scale = palmScale)
            }
        }
    }

    private fun fingerExtension(points: List<Vector3>, indexes: FingerLandmarkIndexes, palmAxes: PalmAxes): Double? {
        val mcp = points[indexes.mcp]
        val pip = points[indexes.pip]
        val dip = points[indexes.dip]
        val tip = points[indexes.tip]
        val firstBoneLength = mcp.distanceTo(pip)
        val secondBoneLength = pip.distanceTo(dip)
        val thirdBoneLength = dip.distanceTo(tip)
        if (!areUsableBones(palmAxes.scale, firstBoneLength, secondBoneLength, thirdBoneLength)) {
            return null
        }

        val pathLength = firstBoneLength + secondBoneLength + thirdBoneLength
        val pipStraightness = jointStraightness(mcp, pip, dip, palmAxes.scale)
        val dipStraightness = jointStraightness(pip, dip, tip, palmAxes.scale)
        val linearity = mcp.distanceTo(tip) / pathLength
        val forwardReach = (tip - mcp).dot(palmAxes.forward) / pathLength

        return if (pipStraightness == null || dipStraightness == null) {
            null
        } else {
            ScoreMath.weightedScore(
                PIP_STRAIGHTNESS_WEIGHT to ScoreMath.ramp(pipStraightness, MIN_STRAIGHT_ANGLE, FULLY_STRAIGHT_ANGLE),
                DIP_STRAIGHTNESS_WEIGHT to ScoreMath.ramp(dipStraightness, MIN_STRAIGHT_ANGLE, FULLY_STRAIGHT_ANGLE),
                LINEARITY_WEIGHT to ScoreMath.ramp(linearity, MIN_LINEARITY, FULL_LINEARITY),
                FORWARD_REACH_WEIGHT to ScoreMath.ramp(forwardReach, MIN_FORWARD_REACH, FULL_FORWARD_REACH)
            )
        }
    }

    private fun thumbExtension(points: List<Vector3>, palmAxes: PalmAxes): Double? {
        val cmc = points[THUMB_CMC]
        val mcp = points[THUMB_MCP]
        val ip = points[THUMB_IP]
        val tip = points[THUMB_TIP]
        val firstBoneLength = cmc.distanceTo(mcp)
        val secondBoneLength = mcp.distanceTo(ip)
        val thirdBoneLength = ip.distanceTo(tip)
        if (!areUsableBones(palmAxes.scale, firstBoneLength, secondBoneLength, thirdBoneLength)) {
            return null
        }

        val pathLength = firstBoneLength + secondBoneLength + thirdBoneLength
        val mcpStraightness = jointStraightness(cmc, mcp, ip, palmAxes.scale)
        val ipStraightness = jointStraightness(mcp, ip, tip, palmAxes.scale)
        val linearity = cmc.distanceTo(tip) / pathLength
        val radialReach = (tip - cmc).dot(palmAxes.radial) / pathLength

        return if (mcpStraightness == null || ipStraightness == null) {
            null
        } else {
            ScoreMath.weightedScore(
                THUMB_MCP_STRAIGHTNESS_WEIGHT to ScoreMath.ramp(mcpStraightness, MIN_STRAIGHT_ANGLE, FULLY_STRAIGHT_ANGLE),
                THUMB_IP_STRAIGHTNESS_WEIGHT to ScoreMath.ramp(ipStraightness, MIN_STRAIGHT_ANGLE, FULLY_STRAIGHT_ANGLE),
                THUMB_LINEARITY_WEIGHT to ScoreMath.ramp(linearity, MIN_THUMB_LINEARITY, FULL_THUMB_LINEARITY),
                THUMB_RADIAL_REACH_WEIGHT to ScoreMath.ramp(radialReach, MIN_THUMB_REACH, FULL_THUMB_REACH)
            )
        }
    }

    private fun jointStraightness(previous: Vector3, joint: Vector3, next: Vector3, referenceScale: Double): Double? {
        val incoming = (previous - joint).normalizedOrNull(referenceScale)
        val outgoing = (next - joint).normalizedOrNull(referenceScale)
        return if (incoming == null || outgoing == null) {
            null
        } else {
            Math.toDegrees(acos(incoming.dot(outgoing).coerceIn(-1.0, 1.0)))
        }
    }

    private fun areUsableBones(referenceScale: Double, vararg lengths: Double): Boolean =
        lengths.all { length -> length.isFinite() && length >= referenceScale * MIN_BONE_LENGTH_RATIO }

    private data class PalmAxes(val radial: Vector3, val forward: Vector3, val scale: Double)

    private data class FingerLandmarkIndexes(val mcp: Int, val pip: Int, val dip: Int, val tip: Int)

    private data class GestureTemplate(val name: String, val expectedExtensions: BooleanArray, val weights: DoubleArray) {
        fun score(fingers: FingerExtensionScores): Double {
            val values = fingers.values()
            val weightedCompatibility = values.indices.sumOf { index ->
                val compatibility = if (expectedExtensions[index]) values[index] else 1.0 - values[index]
                compatibility * weights[index]
            }
            return (weightedCompatibility / weights.sum()).coerceIn(0.0, 1.0)
        }

        fun matchesRequiredFingerStates(fingers: FingerExtensionScores): Boolean = fingers.values().indices.all { index ->
            if (expectedExtensions[index]) {
                fingers.values()[index] >= MIN_EXTENDED_SCORE
            } else {
                fingers.values()[index] <= MAX_CURLED_SCORE
            }
        }
    }

    private data class GestureCandidateScore(val template: GestureTemplate, val score: Double)

    private data class Vector3(val x: Double, val y: Double, val z: Double) {
        operator fun minus(other: Vector3): Vector3 = Vector3(x - other.x, y - other.y, z - other.z)

        operator fun times(multiplier: Double): Vector3 = Vector3(x * multiplier, y * multiplier, z * multiplier)

        fun dot(other: Vector3): Double = x * other.x + y * other.y + z * other.z

        fun cross(other: Vector3): Vector3 = Vector3(
            x = y * other.z - z * other.y,
            y = z * other.x - x * other.z,
            z = x * other.y - y * other.x
        )

        fun length(): Double = sqrt(dot(this))

        fun distanceTo(other: Vector3): Double = (this - other).length()

        fun normalizedOrNull(referenceScale: Double): Vector3? {
            val length = length()
            return if (length.isFinite() && length >= referenceScale * MIN_VECTOR_LENGTH_RATIO) {
                this * (1.0 / length)
            } else {
                null
            }
        }
    }

    private const val LANDMARK_COUNT = 21
    private const val WRIST = 0
    private const val THUMB_CMC = 1
    private const val THUMB_MCP = 2
    private const val THUMB_IP = 3
    private const val THUMB_TIP = 4
    private const val INDEX_MCP = 5
    private const val MIDDLE_MCP = 9
    private const val PINKY_MCP = 17

    private val INDEX_FINGER = FingerLandmarkIndexes(mcp = 5, pip = 6, dip = 7, tip = 8)
    private val MIDDLE_FINGER = FingerLandmarkIndexes(mcp = 9, pip = 10, dip = 11, tip = 12)
    private val RING_FINGER = FingerLandmarkIndexes(mcp = 13, pip = 14, dip = 15, tip = 16)
    private val PINKY_FINGER = FingerLandmarkIndexes(mcp = 17, pip = 18, dip = 19, tip = 20)

    private const val MIN_ABSOLUTE_SCALE = 1e-8
    private const val MIN_PALM_AXIS_RATIO = 0.15
    private const val MIN_ORTHOGONAL_AXIS_LENGTH = 0.99
    private const val MIN_VECTOR_LENGTH_RATIO = 1e-6
    private const val MIN_BONE_LENGTH_RATIO = 0.02

    private const val MIN_STRAIGHT_ANGLE = 110.0
    private const val FULLY_STRAIGHT_ANGLE = 165.0
    private const val MIN_LINEARITY = 0.55
    private const val FULL_LINEARITY = 0.92
    private const val MIN_FORWARD_REACH = 0.15
    private const val FULL_FORWARD_REACH = 0.72
    private const val MIN_THUMB_LINEARITY = 0.55
    private const val FULL_THUMB_LINEARITY = 0.92
    private const val MIN_THUMB_REACH = 0.20
    private const val FULL_THUMB_REACH = 0.72

    private const val PIP_STRAIGHTNESS_WEIGHT = 0.30
    private const val DIP_STRAIGHTNESS_WEIGHT = 0.20
    private const val LINEARITY_WEIGHT = 0.30
    private const val FORWARD_REACH_WEIGHT = 0.20
    private const val THUMB_MCP_STRAIGHTNESS_WEIGHT = 0.25
    private const val THUMB_IP_STRAIGHTNESS_WEIGHT = 0.20
    private const val THUMB_LINEARITY_WEIGHT = 0.20
    private const val THUMB_RADIAL_REACH_WEIGHT = 0.35

    private const val MIN_EXTENDED_SCORE = 0.74
    private const val MAX_CURLED_SCORE = 0.32
    private const val MIN_GESTURE_SCORE = 0.82
    private const val MIN_GESTURE_MARGIN = 0.12

    private val GESTURE_TEMPLATES = listOf(
        GestureTemplate(
            name = "Pointing_Up",
            expectedExtensions = booleanArrayOf(false, true, false, false, false),
            weights = doubleArrayOf(0.45, 1.25, 1.0, 1.0, 0.9)
        ),
        GestureTemplate(
            name = "Victory",
            expectedExtensions = booleanArrayOf(false, true, true, false, false),
            weights = doubleArrayOf(0.45, 1.15, 1.15, 1.0, 0.9)
        ),
        GestureTemplate(
            name = "ILoveYou",
            expectedExtensions = booleanArrayOf(true, true, false, false, true),
            weights = doubleArrayOf(1.15, 1.0, 1.0, 1.0, 1.05)
        )
    )
}

private fun GestureLandmark3D.hasFiniteCoordinates(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

private fun FingerExtensionScores.values(): DoubleArray = doubleArrayOf(
    thumb.toDouble(),
    index.toDouble(),
    middle.toDouble(),
    ring.toDouble(),
    pinky.toDouble()
)

private object ScoreMath {
    fun weightedScore(vararg components: Pair<Double, Double>): Double {
        val totalWeight = components.sumOf { component -> component.first }
        return (components.sumOf { component -> component.first * component.second } / totalWeight).coerceIn(0.0, 1.0)
    }

    fun ramp(value: Double, minimum: Double, maximum: Double): Double = ((value - minimum) / (maximum - minimum)).coerceIn(0.0, 1.0)
}
