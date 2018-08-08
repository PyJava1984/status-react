(ns status-im.ui.screens.wallet.models
  (:require [clojure.set :as set]
            [re-frame.core :as re-frame]
            [status-im.i18n :as i18n]
            [status-im.ui.screens.wallet.navigation]
            [status-im.utils.ethereum.core :as ethereum]
            [status-im.utils.ethereum.erc20 :as erc20]
            [status-im.utils.ethereum.tokens :as tokens]
            [status-im.utils.handlers :as handlers]
            [status-im.utils.prices :as prices]
            [status-im.utils.transactions :as transactions]
            [status-im.models.wallet :as models.wallet]
            [taoensso.timbre :as log]
            status-im.ui.screens.wallet.request.events
            [status-im.constants :as constants]
            [status-im.ui.screens.navigation :as navigation]
            [status-im.utils.money :as money]
            [status-im.wallet.transactions :as wallet.transactions]))

(defn get-balance [{:keys [web3 account-id on-success on-error]}]
  (if (and web3 account-id)
    (.getBalance
     (.-eth web3)
     account-id
     (fn [err resp]
       (if-not err
         (on-success resp)
         (on-error err))))
    (on-error "web3 or account-id not available")))

(defn get-single-token-balance [{:keys [web3 contract account-id on-success on-error]}]
  (if (and web3 contract account-id)
    (erc20/balance-of
     web3
     contract
     (ethereum/normalized-address account-id)
     (fn [err resp]
       (if-not err
         (on-success resp)
         (on-error err))))
    (on-error "web3, contract or account-id not available")))

(defn assoc-error-message [db error-type err]
  (assoc-in db [:wallet :errors error-type] (or (when err (str err))
                                                :unknown-error)))

(defn clear-error-message [db error-type]
  (update-in db [:wallet :errors] dissoc error-type))

;; FX

(defn get-all-token-balances [{:keys [web3 symbols chain account-id success-event error-event]}]
  (doseq [symbol symbols]
    (let [contract (:address (tokens/symbol->token chain symbol))]
      (get-single-token-balance {:web3 web3
                                 :contract    contract
                                 :account-id  account-id
                                 :on-success  #(re-frame/dispatch [success-event symbol %])
                                 :on-error    #(re-frame/dispatch [error-event symbol %])}))))

(defn get-transactions [{:keys [web3 chain account-id token-addresses success-event error-event]}]
  (transactions/get-transactions chain
                                 account-id
                                 #(re-frame/dispatch [success-event % account-id])
                                 #(re-frame/dispatch [error-event %]))
  (doseq [direction [:inbound :outbound]]
    (erc20/get-token-transactions web3
                                  chain
                                  token-addresses
                                  direction
                                  account-id
                                  #(re-frame/dispatch [success-event % account-id]))))

(defn- tokens-symbols [v chain]
  (set/difference (set v) (set (map :symbol (tokens/nfts-for chain)))))

(defn update-wallet [{{:keys [web3 network network-status] {:keys [address settings]} :account/account :as db} :db}]
  (let [network     (get-in db [:account/account :networks network])
        chain       (ethereum/network->chain-keyword network)
        mainnet?    (= :mainnet chain)
        assets      (get-in settings [:wallet :visible-tokens chain])
        tokens      (tokens-symbols (get-in settings [:wallet :visible-tokens chain]) chain)
        currency-id (or (get-in settings [:wallet :currency]) :usd)
        currency    (get constants/currencies currency-id)]
    (when (not= network-status :offline)
      {:get-balance        {:web3          web3
                            :account-id    address
                            :success-event :update-balance-success
                            :error-event   :update-balance-fail}
       :get-tokens-balance {:web3          web3
                            :account-id    address
                            :symbols       assets
                            :chain         chain
                            :success-event :update-token-balance-success
                            :error-event   :update-token-balance-fail}
       :get-prices         {:from          (if mainnet? (conj tokens "ETH") ["ETH"])
                            :to            [(:code currency)]
                            :success-event :update-prices-success
                            :error-event   :update-prices-fail}
       :db                 (-> db
                               (clear-error-message :prices-update)
                               (clear-error-message :balance-update)
                               (assoc-in [:wallet :balance-loading?] true)
                               (assoc :prices-loading? true))})))

;; Handlers

(defn- combine-entries [transaction token-transfer]
  (merge transaction (select-keys token-transfer [:symbol :from :to :value :type :token :transfer])))

(defn- update-confirmations [tx1 tx2]
  (assoc tx1 :confirmations (max (:confirmations tx1)
                                 (:confirmations tx2))))

(defn- tx-and-transfer?
  "A helper function that checks if first argument is a transaction and the second argument a token transfer object."
  [tx1 tx2]
  (and (not (:transfer tx1)) (:transfer tx2)))

(defn- both-transfer?
  [tx1 tx2]
  (and (:transfer tx1) (:transfer tx2)))

(defn- dedupe-transactions [tx1 tx2]
  (cond (tx-and-transfer? tx1 tx2) (combine-entries tx1 tx2)
        (tx-and-transfer? tx2 tx1) (combine-entries tx2 tx1)
        (both-transfer? tx1 tx2)   (update-confirmations tx1 tx2)
        :else tx2))

(defn- own-transaction? [address [_ {:keys [type to from]}]]
  (let [normalized (ethereum/normalized-address address)]
    (or (and (= :inbound type) (= normalized (ethereum/normalized-address to)))
        (and (= :outbound type) (= normalized (ethereum/normalized-address from)))
        (and (= :failed type) (= normalized (ethereum/normalized-address from))))))

(defn on-update-transaction-success [transactions address cofx]
  ;; NOTE(goranjovic): we want to only show transactions that belong to the current account
  ;; this filter is to prevent any late transaction updates initated from another account on the same
  ;; device from being applied in the current account.
  (let [own-transactions (into {} (filter #(own-transaction? address %) transactions))]
    (-> cofx
        (update-in [:db :wallet :transactions] #(merge-with dedupe-transactions % own-transactions))
        (assoc-in [:db :wallet :transactions-loading?] false))))

(defn on-update-transaction-fail [err cofx]
  (log/debug "Unable to get transactions: " err)
  (-> cofx
      (update :db assoc-error-message :transactions-update err)
      (assoc-in [:db :wallet :transactions-loading?] false)))

(defn on-update-balance-success [balance cofx]
  (-> cofx
      (assoc-in [:db :wallet :balance :ETH] balance)
      (assoc-in [:db :wallet :balance-loading?] false)))

(defn on-update-balance-fail [err cofx]
  (log/debug "Unable to get balance: " err)
  (-> cofx
      (update :db assoc-error-message :balance-update err)
      (assoc-in [:db :wallet :balance-loading?] false)))

(defn on-update-token-balance-success [symbol balance {:keys [db]}]
  {:db (-> db
           (assoc-in [:wallet :balance symbol] balance)
           (assoc-in [:wallet :balance-loading?] false))})

(defn on-update-token-balance-fail [symbol err cofx]
  (log/debug "Unable to get token " symbol "balance: " err)
  (-> cofx
      (update :db assoc-error-message :balance-update err)
      (assoc-in [:db :wallet :balance-loading?] false)))

(defn on-update-prices-success [prices {:keys [db]}]
  {:db (assoc db
              :prices prices
              :prices-loading? false)})

(defn on-update-prices-fail [err {:keys [db]}]
  (log/debug "Unable to get prices: " err)
  {:db (-> db
           (assoc-error-message :prices-update err)
           (assoc :prices-loading? false))})

(defn show-transaction-details [hash {:keys [db]}]
  {:db       (assoc-in db [:wallet :current-transaction] hash)
   :dispatch [:navigate-to :wallet-transaction-details]})

(defn show-sign-transaction [{:keys [id method]} from-chat? {:keys [db]}]
  {:db       (assoc-in db [:wallet :send-transaction] {:id         id
                                                       :method     method
                                                       :from-chat? from-chat?})
   :dispatch [:navigate-to-modal :wallet-send-transaction-modal]})

(defn on-update-gas-price-success [price edit? {:keys [db]}]
  {:db (if edit?
         (:db (models.wallet/edit-value
               :gas-price
               (money/to-fixed
                (money/wei-> :gwei price))
               {:db db}))
         (assoc-in db [:wallet :send-transaction :gas-price] price))})

(defn update-estimated-gas [obj {:keys [db]}]
  {:update-estimated-gas {:web3          (:web3 db)
                          :obj           obj
                          :success-event :wallet/update-estimated-gas-success}})

(defn on-update-estimated-gas-success [gas {:keys [db]}]
  {:db (if gas
         (assoc-in db [:wallet :send-transaction :gas] (money/bignumber (int (* gas 1.2))))
         db)})

(defn show-error []
  {:show-error (i18n/label :t/wallet-error)})

(defn wallet-setup-navigate-back [{:keys [db]}]
  {:db (-> db
           (assoc-in [:wallet :send-transaction] {})
           (navigation/navigate-back))})
