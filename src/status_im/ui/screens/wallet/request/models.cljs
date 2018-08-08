(ns status-im.ui.screens.wallet.request.models
  (:require [status-im.ui.screens.wallet.db :as wallet-db]
            [status-im.utils.handlers-macro :as handlers-macro]
            [status-im.chat.commands.core :as commands]
            [status-im.utils.money :as money]))

(defn send-request-chat-message [asset amount {:keys [db] :as cofx}]
  (handlers-macro/merge-fx cofx
                           {:dispatch [:send-current-message]}
                           (commands/select-chat-input-command
                            (get-in db [:id->command ["request" #{:personal-chats}]]) [asset amount])))

(defn send-request [whisper-identity amount symbol decimals _cofx]
  (assert whisper-identity)
  ;; TODO(janherich) remove this dispatch sequence, there is absolutely no need for that :/
  {:dispatch-n [[:navigate-back]
                [:navigate-to-clean :home]
                [:add-chat-loaded-event whisper-identity
                 [:wallet-send-chat-request (name symbol) (str (money/internal->formatted amount symbol decimals))]]
                [:start-chat whisper-identity]]})

(defn set-recipient [recipient {:keys [db]}]
  {:db (assoc-in db [:wallet :request-transaction :to] recipient)})

(defn set-and-validate-amount [amount symbol decimals {:keys [db]}]
  (let [{:keys [value error]} (wallet-db/parse-amount amount decimals)]
    {:db (-> db
             (assoc-in [:wallet :request-transaction :amount] (money/formatted->internal value symbol decimals))
             (assoc-in [:wallet :request-transaction :amount-text] amount)
             (assoc-in [:wallet :request-transaction :amount-error] error))}))

(defn set-symbol [symbol {:keys [db]}]
  {:db (-> db
           (assoc-in [:wallet :request-transaction :symbol] symbol))})
