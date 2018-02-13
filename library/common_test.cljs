(ns hedge.common-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hedge.common :refer [outputs->atoms]]))

(def outputs [{:type :queue
               :key "out1"
               :name "queue2"
               :connection "AzureWebJobsStorage"}
             {:type :queue
               :key "out2"
               :name "queue2"
               :connection "AzureWebJobsStorage"}])
          
(deftest outputs->atoms-test
  (testing "if data from hedge.edn can define outputs to handler function"
    (let [result (outputs->atoms outputs)]
      (reset! (-> result :out2) "setted value")
      (is (nil? @(-> result :out1)))
      (is (= "setted value" @(-> result :out2))))))
