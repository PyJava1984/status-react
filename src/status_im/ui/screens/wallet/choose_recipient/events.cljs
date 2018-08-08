(ns status-im.ui.screens.wallet.choose-recipient.events
  (:require [status-im.utils.handlers :as handlers]
            [status-im.ui.screens.wallet.choose-recipient.models :as models]))

(handlers/register-handler-fx
 :wallet/toggle-flashlight
 (fn [cofx]
   (models/toggle-flashlight cofx)))

(handlers/register-handler-fx
 :wallet/fill-request-from-url
 (fn [cofx [_ data origin]]
   (models/fill-request-from-url data origin cofx)))

(handlers/register-handler-fx
 :wallet/fill-request-from-contact
 (fn [cofx [_ contact]]
   (models/fill-request-from-contact contact cofx)))
