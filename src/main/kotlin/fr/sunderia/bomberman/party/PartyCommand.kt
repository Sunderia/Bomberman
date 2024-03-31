package fr.sunderia.bomberman.party

import fr.sunderia.bomberman.party.Party.Companion.createParty
import fr.sunderia.bomberman.party.Party.Companion.removePlayerFromParty
import fr.sunderia.bomberman.party.Party.Companion.getParty
import fr.sunderia.bomberman.party.Party.Companion.joinParty
import fr.sunderia.bomberman.party.Party.Companion.sendInvite
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.command.CommandSender
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.CommandContext
import net.minestom.server.command.builder.CommandExecutor
import net.minestom.server.command.builder.arguments.ArgumentEnum
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player

class PartyCommand: Command("party", "p") {

    private enum class SubCommand {
        INVITE,
        JOIN,
        LEAVE,
        LIST,
        CREATE
    }

    init {
        // Executed if no other executor can be used
        defaultExecutor =
            CommandExecutor { sender: CommandSender, _: CommandContext? ->
                sender.sendMessage("You executed the command")
            }

        val subcommand = ArgumentType.Enum("subcommand", SubCommand::class.java)
            .setFormat(ArgumentEnum.Format.LOWER_CASED)
        val playerName = ArgumentType.Entity("target").onlyPlayers(true).singleEntity(true)

        subcommand.setCallback { sender: CommandSender, exception ->
            sender.sendMessage(
                Component.text("Invalid subcommand ", NamedTextColor.RED)
                    .append(Component.text(exception.input, NamedTextColor.WHITE))
                    .append(Component.text("!"))
            )
        }

        setDefaultExecutor { sender, context ->
            sender.sendMessage(
                Component.text(
                    "Usage: /${context.commandName} <subcommand> [player]",
                    NamedTextColor.RED
                )
            )
        }

        addSyntax({ sender: CommandSender, context: CommandContext ->
            if(sender !is Player) return@addSyntax
            when(context.get(subcommand)) {
                SubCommand.LIST -> {
                    val party = getParty(sender)
                    if(party == null) {
                        sender.sendMessage(Component.text("You are not in any party.").color(NamedTextColor.RED))
                        return@addSyntax
                    }

                    sender.sendMessage(Component.text {
                        it.append(Component.text("Party:")).appendNewline()
                        it.append(Component.text("  - "))
                            .append(Component.text(party.host.username).color(NamedTextColor.RED))
                            .appendNewline()
                        party.playerList.forEach { player ->
                            it.append(Component.text("  - ${player.username}")).appendNewline()
                        }
                    })
                }
                SubCommand.CREATE -> {
                    createParty(sender)
                    sender.sendMessage(Component.text(" > Successfully created a party"))
                }
                SubCommand.LEAVE -> {
                    val party = getParty(sender)
                    if(party == null) {
                        sender.sendMessage(Component.text("You are not in any party.").color(NamedTextColor.RED))
                        return@addSyntax
                    }

                    if(party.playerList.isEmpty()) {
                        sender.sendMessage(Component.text(" > Successfully deleted the party."))
                        removePlayerFromParty(sender)
                        return@addSyntax
                    }

                    if(party.host.uuid == sender.uuid) {
                        party.host = party.playerList.minByOrNull { it.username }!!
                        sender.sendMessage(Component.text("Successfully left your party."))
                        party.playerList.forEach {
                            it.sendMessage(Component.text(" > ${sender.username} left the party, the new host is ${party.host.username}"))
                        }
                        party.playerList.remove(party.host)
                        removePlayerFromParty(sender)
                        return@addSyntax
                    }

                    party.playerList.remove(sender)
                    sender.sendMessage(Component.text {
                        it.append(Component.text(" > Successfully left "))
                        it.append(Component.text("${party.host.username}'s").color(NamedTextColor.GREEN))
                        it.append(Component.text(" party"))
                    })
                    party.playerList.forEach {
                        it.sendMessage(Component.text(" > ${sender.username} left the party"))
                    }
                }
                else -> {}
            }
        }, subcommand)

        addSyntax({ sender: CommandSender, context: CommandContext ->
            if(sender !is Player) return@addSyntax
            val target = context.get(playerName).find(sender)[0] as Player
            val subCommandType = context.get(subcommand)
            if(subCommandType == SubCommand.INVITE) {
                val party = getParty(sender)
                if(party == null) {
                    sender.sendMessage(Component.text("You are not in any party.").color(NamedTextColor.RED))
                    return@addSyntax
                }
                if(!target.isOnline) {
                    sender.sendMessage(Component.text("${target.username} is not online.").color(NamedTextColor.RED))
                    return@addSyntax
                }
                sendInvite(target, party)
                sender.sendMessage(Component.text("An invite has been sent to ${target.username}"))
            } else if(subCommandType == SubCommand.JOIN) {
                val senderParty = getParty(sender)
                if(senderParty != null) {
                    sender.sendMessage(Component.text("You are already in a party.").color(NamedTextColor.RED))
                    return@addSyntax
                }
                val hostParty = getParty(target)
                if(hostParty == null) {
                    sender.sendMessage(Component.text("The player ${target.username} is not in a party.").color(NamedTextColor.RED))
                    return@addSyntax
                }
                if(hostParty.invites.none { it.uuid == sender.uuid }) {
                    sender.sendMessage(Component.text("You haven't recieved any invites from this party.").color(NamedTextColor.RED))
                    return@addSyntax
                }
                joinParty(sender, hostParty)
            }
        }, subcommand, playerName)
    }
}