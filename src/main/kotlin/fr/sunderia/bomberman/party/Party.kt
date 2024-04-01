package fr.sunderia.bomberman.party

import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance

private typealias PartyType = MutableMap<Player, Party>

data class Party(var host: Player, val playerList: MutableSet<Player> = mutableSetOf(), val invites: MutableSet<Player> = mutableSetOf()) {
    companion object {
        private val parties: PartyType = mutableMapOf()

        fun getParty(player: Player): Party? {
            return parties[player]
        }

        fun createParty(player: Player) {
            parties[player] = Party(player)
        }

        fun removePlayerFromParty(player: Player) {
            val party = parties[player] ?: return
            if(party.playerList.isEmpty()) {
                player.sendMessage(text(" > Successfully deleted the party."))
                parties.remove(player)!!.playerList.remove(player)
                return
            }
            if(party.host.uuid == player.uuid) {
                party.host = party.playerList.minByOrNull { it.username }!!
                player.sendMessage(text("Successfully left your party."))
                party.playerList.forEach {
                    it.sendMessage(text(" > ${player.username} left the party, the new host is ${party.host.username}"))
                }
                party.playerList.remove(party.host)
                parties.remove(player)!!.playerList.remove(player)
                return
            }
            party.playerList.remove(player)
            player.sendMessage(text {
                it.append(text(" > Successfully left "))
                it.append(text("${party.host.username}'s").color(NamedTextColor.GREEN))
                it.append(text(" party"))
            })
            party.playerList.forEach {
                it.sendMessage(text(" > ${player.username} left the party"))
            }
        }
    }

    fun sendInvite(player: Player) {
        this.invites.add(player)
        player.sendMessage(text {
            it.append(text("> You have been invited to "))
                    .append(text("${this.host.username}'s").color(NamedTextColor.GREEN))
                    .append(text(" party"))
                    .appendNewline()
                    .append(text("To join his party, write "))
                    .append(text("/party join ${this.host.username}").decorate(TextDecoration.UNDERLINED).color(NamedTextColor.AQUA))
                    .clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, "/party join ${this.host.username}"))
        })
    }

    fun joinParty(player: Player) {
        this.playerList.forEach {
            it.sendMessage(text(" > ${player.username} joined the party."))
        }
        this.host.sendMessage(text(" > ${player.username} joined the party."))
        this.playerList.add(player)
        this.invites.remove(player)
        parties[player] = this
        player.sendMessage(text("You have successfully joined ${this.host.username}'s party"))
    }

    fun warp(instance: Instance) {
        this.host.instance = instance
        this.host.sendMessage(text("Successfully sent you to a Bomberman game"))
        this.playerList.forEach {
            it.instance = instance
            it.sendMessage(text("Successfully sent you to a Bomberman game"))
        }
    }
}