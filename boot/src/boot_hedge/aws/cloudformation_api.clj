(ns boot-hedge.aws.cloudformation-api
  (:require [clojure.pprint]
            [boot-hedge.common.core :refer [now]])
  (:import [com.amazonaws.services.cloudformation AmazonCloudFormationClientBuilder]
           [com.amazonaws.regions Regions]
           [com.amazonaws.services.cloudformation.model DescribeStacksRequest]
           [com.amazonaws.services.cloudformation.model DescribeChangeSetRequest]
           [com.amazonaws.services.cloudformation.model AmazonCloudFormationException]
           [com.amazonaws.services.cloudformation.model CreateChangeSetRequest]
           [com.amazonaws.services.cloudformation.model ChangeSetType]
           [com.amazonaws.services.cloudformation.model ExecuteChangeSetRequest]
           [com.amazonaws.services.cloudformation.model Capability]
           [com.amazonaws.waiters WaiterParameters]
           [com.amazonaws.util DateUtils]))

; this should be autogenerated
(def todo-changeset-name "TODO-change-this-asap")

; API endpoint communication
(defn client
  "Creates AmazonCloudFormationClient which is used by other API methods"
  []
  (-> (AmazonCloudFormationClientBuilder/standard)
   (.withRegion Regions/EU_WEST_3)
   (.build)))

(defn describe-stacks
  "Gets list of stacks or named stack"
  ([client]
   (.describeStacks client))
  ([client stack-name]
   (as-> (DescribeStacksRequest.) n
    (.withStackName n stack-name)
    (.describeStacks client n))))

(defn describe-changeset
  "Gets changeset info"
  [client changeset-id]
  (as-> (DescribeChangeSetRequest.) n
   (.withChangeSetName n changeset-id)
   (.describeChangeSet client n)))

(defn create-changeset
  "Creates new changeset with changeset request"
  [client changeset-request]
  (.createChangeSet client changeset-request))

(defn execute-changeset
  "Executes changeset"
  [client changeset-id]
  (as-> (ExecuteChangeSetRequest.) n
    (.withChangeSetName n changeset-id)
    (.executeChangeSet client n)))

(defn stack-exists?
  "Checks if given stack exists"
  [client stack-name]
  (try
    (let [result (describe-stacks client stack-name)
          size (count (.getStacks result))]
      (= 1 size))  
    (catch AmazonCloudFormationException e false)))

(defn stack-status
  "Gets status of given stack"
  [client stack-name]
  (try
    (let [result (describe-stacks client stack-name)
          stack (first (.getStacks result))]
      (.getStackStatus stack))
    (catch AmazonCloudFormationException e
      (throw (Exception. "API call failed" e)))))

(defn changeset-status
  "Gets status of given stack"
  [client changeset-id]
  (try
    (let [result (describe-changeset client changeset-id)
          status (.getStatus result)]
      status)
    (catch AmazonCloudFormationException e
      (throw (Exception. "API call failed" e)))))

; other methods
(defn poller
  "helper poller for wait-for* methods."
  [client name-or-id f ok-statuses fail-statuses]
  ; TODO rewrite, please
  (let [count (atom 0)
        result (atom nil)]
    (while (< @count 720)
      (let [status (f client name-or-id)
            ok (contains? ok-statuses status)
            fail (contains? fail-statuses status)]
        (println "Status in now " status)
        (when (or ok fail) 
          (swap! count + 721)
          (reset! result status)))
      (Thread/sleep 5000))
    @result))

(defn wait-for-changeset
  "Wait until changset is created"
  [client changeset-id]
  (poller client changeset-id changeset-status #{"CREATE_COMPLETE"} 
          #{"FAILED"}))

(defn wait-for-execute
  "Wait until changset is created"
  [client stack-name]
  (poller client stack-name stack-status #{"CREATE_COMPLETE" "UPDATE_COMPLETE"} 
          {"ROLLBACK_COMPLETE" "ROLLBACK_IN_PROGRESS"}))

;logic
(defn create-or-update-stack
  "Construct stack create or update request and starts creation process"
  [client stack-name template type]
  (let [description (str "Created with Hedge at " (DateUtils/formatISO8601Date (now)))
        create-or-update-stack-request 
        (-> (CreateChangeSetRequest.)
          (.withStackName stack-name)
          (.withChangeSetName todo-changeset-name) ; TODO make more unique
          (.withCapabilities ["CAPABILITY_IAM"])     ; TODO use types from SDK
          (.withChangeSetType type)
          (.withDescription description)
          (.withTemplateBody template))]
    (create-changeset client create-or-update-stack-request)))

(defn create-stack
  "Create new stack"
  [client stack-name template]
  (create-or-update-stack client stack-name template ChangeSetType/CREATE))

(defn update-stack
  "Update stack"
  [client stack-name template]
  (create-or-update-stack client stack-name template ChangeSetType/UPDATE))

(defn deploy-stack
  "Create or deploy stack with given name and template"
  [client stack-name template]
  (let [template-string (slurp template)
        f (if (stack-exists? client stack-name) 
            (do (println "Stack exists, updating stack") update-stack) 
            (do (println "Stack not found, creating new stack") create-stack))
        create-changeset-result (f client stack-name template-string)
        changeset-id (.getId create-changeset-result)]
    (println (str "Stack status changed to " (wait-for-changeset client changeset-id)))
    (let [execute-changeset-result (execute-changeset client changeset-id)]
      (println (str "Stack status changed to " (wait-for-execute client stack-name)))
      (println "Stack is ready!")
      (clojure.pprint/pprint (-> (describe-stacks client stack-name)
                                 (.getStacks)
                                 (first)
                                 (.getOutputs))))))
