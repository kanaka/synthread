(ns lonocloud.synthread
  (:use [lonocloud.synthread.isolate :only [isolate-ns]]))
(isolate-ns :as ->)

;; TODO review performance of nth and last.

;; (oh yeah. we're messing with if :-)
(defmacro if
  "If pred is true, thread x through the then form, otherwise through
  the else form.
  (-> 5 (->/if should-inc? inc dec))"
  [x pred then else]
  `(let [x# ~x]
     (if ~pred
       (-> x# ~then)
       (-> x# ~else))))

(defmacro if-let
  "Thread x through then or else depending on the value of pred. If
  pred is true, bind local to pred.
  (-> {}
    (->/if-let [x :bar]
      (assoc :foo x)
      (assoc :was-bar false)))
  ;; returns {:foo :bar}"
  [x [local pred] then else]
  `(let [x# ~x]
     (if-let [~local ~pred]
       (-> x# ~then)
       (-> x# ~else))))

(defmacro when
  "If pred is true, thread x through body, otherwise return x unchanged.
  (-> 5 (->/when should-inc? inc))"
  [x pred & body]
  `(let [x# ~x]
     (if ~pred
       (-> x# ~@body)
       x#)))

(defmacro when-not
  "If pred is false, thread x through body, otherwise return x unchanged.
  (-> 5 (->/when should-inc? inc))"
  [x pred & body]
  `(let [x# ~x]
     (if ~pred
       x#
       (-> x# ~@body))))

(defmacro when-let
  "If bound values are true in bindings, thread x through the body,
  otherwise return x unchanged.
  (-> 5 (->/when-let [amount (:amount foo)] (+ amount)))"
  [x bindings & forms]
  `(let [x# ~x]
     (if-let ~bindings
       (-> x# ~@forms)
       x#)))

(defmacro cond
  "EXPERIMENTAL Thread x through forms in each clause. Return x if no test matches.
  (->/cond [1 2] true (conj 3) false pop)"
  [x & test-form-pairs]
  (let [xx (gensym)]
    `(let [~xx ~x]
       (cond ~@(mapcat (fn [[test form]] `[~test (-> ~xx ~form)])
                       (partition 2 test-form-pairs))
             :else ~xx))))

(defmacro for
  "Thread x through each iteration of body. Uses standard looping
  binding syntax for iterating.
  (->/for 4 [x [1 2 3]] (+ x)) ;; returns 10"
  [x seq-exprs & body]
  `(let [box# (clojure.lang.Box. ~x)]
     (doseq ~seq-exprs
       (set! (.val box#) (-> (.val box#) ~@body)))
     (.val box#)))

(defmacro first
  "Thread the first element of x through body.
  (->/first [1 2 3] inc -) ;; returns [-2 2 3]"
  [x & body]
  `(let [x# ~x]
     (cons (-> (first x#) ~@body)
           (rest x#))))

(defmacro second
  "Thread the second element of x through body.
  (->/second [1 2 3] inc -) ;; returns [1 -3 3]"
  [x & body]
  `(let [x# ~x]
     (cons (first x#)
           (cons (-> (second x#) ~@body)
                 (drop 2 x#)))))

(defmacro nth
  "EXPERIMENTAL Thread the nth element of x through body.
  (->/nth [1 2 3] 1 inc -) ;; returns [1 -3 3]"
  [x n & body]
  `(let [x# ~x]
     (concat (take ~n x#)
             (cons (-> (nth x# ~n) ~@body)
                   (drop (inc ~n) x#)))))

(defmacro last
  "EXPERIMENTAL Thread the last element of x through body.
  (->/last [1 2 3] inc -) ;; returns [1 2 -4]"
  [x & body]
  `(let [x# ~x]
     (concat (drop-last 1 x#)
             [(-> (last x#) ~@body)])))

(defmacro rest
  "EXPERIMENTAL Thread the rest of items in x through body."
  [x & body]
  `(let [x# ~x] (cons (first x#)
                      (-> (rest x#) ~@body))))

(defmacro assoc
  "Thread the value at each key through the pair form."
  [x & key-form-pairs]
  (let [xx (gensym)]
    `(let [~xx ~x]
       (assoc ~xx
         ~@(->> key-form-pairs
                (partition 2)
                (mapcat (fn [[key form]]
                          [key `(-> (get ~xx ~key) ~form)])))))))

(defmacro in
  "Thread the portion of x specified by path through body.
  (->/in {:a 1, :b 2} [:a] (+ 2)) ;; = {:a 3, :b 2}"
  [x path & body]
  `(if (empty? ~path)
     (-> ~x ~@body)
     (update-in ~x ~path (fn [x#] (-> x# ~@body)))))

(defmacro keys
  "EXPERIMENTAL Thread keys in x through body."
  [x & body]
  `(let [x# ~x]
     (zipmap (-> x# keys ~@body)
             (-> x# vals))))

(defmacro vals
  "EXPERIMENTAL Thread values in x through body."
  [x & body]
  `(let [x# ~x]
     (zipmap (-> x# keys)
             (-> x# vals ~@body))))

(defmacro let
  "Thread x through body (with bindings available as usual).
  (->/let 4 [x 3] (+ x) (- x)) ;; returns 4"
  [x bindings & body]
  `(let [~@bindings
         x# ~x]
     (-> x# ~@body)))

(defmacro fn
  "Thread x into body of fn. (inspired by Prismatic's fn->).
  (let [add-n (->/fn [n] (+ n))]
    (-> 1 (add-n 2))) ;; returns 3"
  [args & body]
  `(fn [x# ~@args] (-> x# ~@body)))

;; Labeling forms (as, as-do, as-to)
;; +----- label value x
;; | +--- modify value x
;; | | +- thread value x
;; | | |
;; 0 0 0 (doto x (do ...))     ;; for side effects, no access to the topic: (prn "hello")
;; 0 0 1 (doto x (-> ...))     ;; almost but not quite (doto x ...) threading but with chained side-effects?
;; 0 1 0 (do x ...)
;; 0 1 1 (-> x ...)
;; 1 0 0 (->/aside x ...)      ;; equivilent to (doto (->/as x (do ...)))
;; 1 0 1 (doto x (->/as ...))
;; 1 1 0 (->/as x (do .... ))  ;; mention in style guide
;; 1 1 1 (->/as x ...)

(defmacro as
  "Bind value of x and thread x through body.
   EXPERIMENTALLY supports arbitrary threading form in place of binding form."
  [x binding & body]
  (if (seq? binding)
    `(let [x# ~x
           ~(last binding) (-> x# ~(drop-last binding))]
       (-> x# ~@body))
    `(let [x# ~x
           ~binding x#]
       (-> x# ~@body))))

(defmacro aside
  "Bind value of x, evaluate unthreaded body and return x."
  [x binding & body]
  `(doto ~x (->/as ~binding (do ~@body))))

(defmacro each
  "EXPERIMENTAL Thread each item in x through body."
  [x & body]
  `(for [x# ~x] (-> x# ~@body)))

(defmacro each-as
  "EXPERIMENTAL Thread each item in x through body and apply binding to each item."
  [x binding & body]
  `(for [x# ~x] (->/as x# ~binding ~@body)))

(defn apply
  "Apply f to x and args."
  [x & f-args]
  (let [[f & args] (concat (drop-last f-args) (last f-args))]
    (apply f x args)))

(defn reset
  "Replace x with y."
  [x y] y)
