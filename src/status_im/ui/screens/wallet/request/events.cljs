(ns status-im.ui.screens.wallet.request.events
  (:require [status-im.utils.handlers :as handlers]
            [status-im.ui.screens.wallet.request.models :as models]))

(handlers/register-handler-fx
 :wallet-send-chat-request
 (fn [cofx [_ asset amount]]
   (models/send-request-chat-message asset amount cofx)))

(handlers/register-handler-fx
 :wallet-send-request
 (fn [cofx [_ whisper-identity amount symbol decimals]]
   (models/send-request whisper-identity amount symbol decimals cofx)))

(handlers/register-handler-fx
 :wallet.request/set-recipient
 (fn [cofx [_ recipient]]
   (models/set-recipient recipient cofx)))

(handlers/register-handler-fx
 :wallet.request/set-and-validate-amount
 (fn [cofx [_ amount symbol decimals]]
   (models/set-and-validate-amount amount symbol decimals cofx)))

(handlers/register-handler-fx
 :wallet.request/set-symbol
 (fn [cofx [_ symbol]]
   (models/set-symbol symbol cofx)))
