package com.poddrive.domain.model

/**
 * LOVED/LIKED tier assigned to a podcast or episode.
 * LOVED episodes are presented before LIKED in the drive briefing (FR15).
 * NONE indicates no tier has been set.
 * [Source: architecture.md#Format Patterns, AR3]
 */
enum class EpisodeTier { LOVED, LIKED, NONE }
