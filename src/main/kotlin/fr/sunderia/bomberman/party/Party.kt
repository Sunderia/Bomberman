package fr.sunderia.bomberman.party

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.entity.Player

private typealias PartyType = MutableMap<Player, Party>

data class Party(var host: Player, val playerList: MutableList<Player> = mutableListOf(), val invites: MutableList<Player> = mutableListOf()) {
    companion object {
        private val parties: PartyType = mutableMapOf()

        fun getParty(player: Player): Party? {
            return parties[player]
        }

        fun createParty(player: Player) {
            parties[player] = Party(player)
        }

        fun removePlayerFromParty(player: Player) {
            parties.remove(player)!!.playerList.remove(player)
        }

        fun sendInvite(player: Player, party: Party) {
            party.invites.add(player)
            player.sendMessage(Component.text {
                it.append(Component.text("> You have been invited to "))
                    .append(Component.text("${party.host.username}'s").color(NamedTextColor.GREEN))
                    .append(Component.text(" party"))
                    .appendNewline()
                    .append(Component.text("To join his party, write "))
                    .append(Component.text("/party join ${party.host.username}").decorate(TextDecoration.UNDERLINED).color(NamedTextColor.AQUA))
                    .clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, "/party join ${party.host.username}"))
            })
        }

        fun joinParty(player: Player, party: Party) {
            party.playerList.forEach {
                it.sendMessage(Component.text(" > ${player.username} joined the party."))
            }
            party.host.sendMessage(Component.text(" > ${player.username} joined the party."))
            party.playerList.add(player)
            party.invites.remove(player)
            parties[player] = party
            player.sendMessage(Component.text("You have successfully joined ${party.host.username}'s party"))
        }
    }
}