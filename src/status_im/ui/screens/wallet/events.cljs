(ns status-im.ui.screens.wallet.events
  (:require [re-frame.core :as re-frame]
            [status-im.ui.screens.wallet.navigation]
            [status-im.utils.ethereum.core :as ethereum]
            [status-im.utils.handlers :as handlers]
            [status-im.utils.prices :as prices]
            status-im.ui.screens.wallet.request.events
            [status-im.ui.screens.wallet.models :as wallet.models]
            [status-im.wallet.transactions :as wallet.transactions]))

(defn assoc-error-message [db error-type err]
  (assoc-in db [:wallet :errors error-type] (or (when err (str err))
                                                :unknown-error)))

(defn clear-error-message [db error-type]
  (update-in db [:wallet :errors] dissoc error-type))

;; FX

(re-frame/reg-fx
 :get-balance
 (fn [{:keys [web3 account-id success-event error-event]}]
   (wallet.models/get-balance {:web3       web3
                               :account-id account-id
                               :on-success #(re-frame/dispatch [success-event %])
                               :on-error   #(re-frame/dispatch [error-event %])})))

(re-frame/reg-fx
 :get-tokens-balance
 (fn [options]
   (wallet.models/get-all-token-balances options)))

(re-frame/reg-fx
 :get-transactions
 (fn [options]
   (wallet.models/get-transactions options)))

;; TODO(oskarth): At some point we want to get list of relevant assets to get prices for
(re-frame/reg-fx
 :get-prices
 (fn [{:keys [from to success-event error-event]}]
   (prices/get-prices from
                      to
                      #(re-frame/dispatch [success-event %])
                      #(re-frame/dispatch [error-event %]))))

(re-frame/reg-fx
 :update-gas-price
 (fn [{:keys [web3 success-event edit?]}]
   (ethereum/gas-price web3 #(re-frame/dispatch [success-event %2 edit?]))))

(re-frame/reg-fx
 :update-estimated-gas
 (fn [{:keys [web3 obj success-event]}]
   (ethereum/estimate-gas-web3 web3 (clj->js obj) #(re-frame/dispatch [success-event %2]))))

;; Handlers
(handlers/register-handler-fx
 :update-wallet
 (fn [cofx _]
   (wallet.models/update-wallet cofx)))

(handlers/register-handler-fx
 :update-transactions
 (fn [cofx _]
   (wallet.transactions/run-update cofx)))

(handlers/register-handler-fx
 :update-transactions-success
 (fn [cofx [_ transactions address]]
   (wallet.models/on-update-transaction-success transactions address cofx)))

(handlers/register-handler-fx
 :update-transactions-fail
 (fn [cofx [_ err]]
   (wallet.models/on-update-transaction-fail err cofx)))

(handlers/register-handler-fx
 :update-balance-success
 (fn [cofx [_ balance]]
   (wallet.models/on-update-balance-success balance cofx)))

(handlers/register-handler-fx
 :update-balance-fail
 (fn [cofx [_ err]]
   (wallet.models/on-update-balance-fail err cofx)))

(handlers/register-handler-fx
 :update-token-balance-success
 (fn [cofx [_ symbol balance]]
   (wallet.models/on-update-token-balance-success symbol balance cofx)))

(handlers/register-handler-fx
 :update-token-balance-fail
 (fn [cofx [_ symbol err]]
   (wallet.models/on-update-token-balance-fail symbol err cofx)))

(handlers/register-handler-fx
 :update-prices-success
 (fn [cofx [_ prices]]
   (wallet.models/on-update-prices-success prices cofx)))

(handlers/register-handler-fx
 :update-prices-fail
 (fn [cofx [_ err]]
   (wallet.models/on-update-prices-fail err cofx)))

(handlers/register-handler-fx
 :show-transaction-details
 (fn [cofx [_ hash]]
   (wallet.models/show-transaction-details hash cofx)))

(handlers/register-handler-fx
 :wallet/show-sign-transaction
 (fn [cofx [_ transaction from-chat?]]
   (wallet.models/show-sign-transaction transaction from-chat? cofx)))

(handlers/register-handler-fx
 :wallet/update-gas-price-success
 (fn [cofx [_ price edit?]]
   (wallet.models/on-update-gas-price-success price edit? cofx)))

(handlers/register-handler-fx
 :wallet/update-estimated-gas
 (fn [cofx [_ obj]]
   (wallet.models/update-estimated-gas obj cofx)))

(handlers/register-handler-fx
 :wallet/update-estimated-gas-success
 (fn [cofx [_ gas]]
   (wallet.models/on-update-estimated-gas-success gas cofx)))

(handlers/register-handler-fx
 :wallet/show-error
 (fn []
   (wallet.models/show-error)))

(handlers/register-handler-fx
 :wallet-setup-navigate-back
 (fn [cofx]
   (wallet.models/wallet-setup-navigate-back cofx)))