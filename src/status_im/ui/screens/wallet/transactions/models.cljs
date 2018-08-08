(ns status-im.ui.screens.wallet.transactions.models)

(defn- mark-all-checked [filters]
  (update filters :type #(map (fn [m] (assoc m :checked? true)) %)))

(defn- mark-checked [filters {:keys [type]} checked?]
  (update filters :type #(map (fn [{:keys [id] :as m}] (if (= type id) (assoc m :checked? checked?) m)) %)))

(defn- update-filters [db f]
  (update-in db [:wallet.transactions :filters] f))

(defn filter-transactions [path checked? {:keys [db]}]
  (update-filters db #(mark-checked % path checked?)))

(defn show-all-transactions [{:keys [db]}]
  (update-filters db mark-all-checked))
