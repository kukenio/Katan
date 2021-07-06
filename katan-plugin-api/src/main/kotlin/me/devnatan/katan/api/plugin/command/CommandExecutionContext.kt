/*
 * Copyright 2020-present Natan Vieira do Nascimento
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.devnatan.katan.api.plugin.command

import org.slf4j.event.Level

interface CommandExecutionContext {

    val label: String

    val args: Array<out String>

}

fun CommandExecutionContext.log(message: String, level: Level = Level.INFO) {
    throw FriendlyCommandException(message, level)
}

fun CommandExecutionContext.fail(message: String, cause: Throwable? = null): Nothing {
    throw CommandException(message, cause)
}