(ns status-im.ui.screens.wallet.transactions.events
  (:require [status-im.utils.handlers :as handlers]
            [status-im.ui.screens.wallet.transactions.models :as models]))

(handlers/register-handler-fx
 :wallet.transactions/filter
 (fn [cofx [_ path checked?]]
   (models/filter-transactions path checked? cofx)))

(handlers/register-handler-fx
 :wallet.transactions/filter-all
 (fn [cofx]
   (models/show-all-transactions cofx)))
