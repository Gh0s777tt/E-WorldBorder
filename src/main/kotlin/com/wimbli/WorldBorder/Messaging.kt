package com.wimbli.WorldBorder

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.command.CommandSender

// Adventure serializer for our internal legacy section (§) formatted strings.
private val LEGACY = LegacyComponentSerializer.legacySection()

/** Parse a legacy section-formatted (§) string into an Adventure Component. */
fun legacyComponent(text: String): Component = LEGACY.deserialize(text)

/**
 * Send [text] to this sender as an Adventure Component, parsed from a legacy
 * section-formatted (§) string. This replaces the deprecated, non-Adventure
 * CommandSender.sendMessage(String) path while keeping the existing colored-string
 * building logic untouched.
 */
fun CommandSender.msg(text: String) = sendMessage(LEGACY.deserialize(text))
