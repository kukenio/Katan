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

package me.devnatan.katan.api.event.account

import me.devnatan.katan.api.security.account.Account


/**
 * Called when a new [Account] is created.
 * Do not confuse creation with registration, for registration use [AccountRegisterEvent].
 * @property account the account that was created.
 */
open class AccountCreateEvent(override val account: Account) : AccountEvent