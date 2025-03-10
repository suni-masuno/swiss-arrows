(ns swiss.arrows)

(defmacro ^:internal -<>*
  "helper macro used by public API macros -<> and -<>>.
   Inserts x in place of '<>' in form, or in first or last position as indicated
   by default-position (which 'traditional arrow' semantics to fall back on when
   no position is explicitly specified by a diamond)"
  [form x default-position]
  (let [substitute-pos (fn [form'] (replace {'<> x} form'))
        count-pos (fn [form'] (count (filter (partial = '<>) form')))
        c (cond
           (or (seq? form) (vector? form)) (count-pos form)
           (map? form) (count-pos (mapcat concat form))
           :otherwise 0)]
    (cond
     (> c 1)              (throw
                           (Exception.
                            "No more than one position per form is allowed."))
     (or (symbol? form)
         (keyword? form)) `(~form ~x)
     (= 0 c)              (cond (vector? form)
                                (if (= :first default-position)
                                  `(vec (cons ~x ~form))
                                  `(conj ~form ~x)) ,
                                (coll? form)
                                (if (= :first default-position)
                                  `(~(first form) ~x ~@(next form))
                                  `(~(first form) ~@(next form) ~x)) ,
                                :otherwise form)
     (vector? form)       (substitute-pos form)
     (map? form)          (apply hash-map (mapcat substitute-pos form))
     (= 1 c)              `(~(first form) ~@(substitute-pos (next form))))))

(defmacro -<>
  "the 'diamond wand': top-level insertion of x in place of single
   positional '<>' symbol within the threaded form if present, otherwise
   mostly behave as the thread-first macro. Also works with hash literals
   and vectors."
  ([x] x)
  ([x form] `(-<>* ~form ~x :first))
  ([x form & forms] `(-<> (-<> ~x ~form) ~@forms)))

(defmacro -<>>
  "the 'diamond spear': top-level insertion of x in place of single
   positional '<>' symbol within the threaded form if present, otherwise
   mostly behave as the thread-last macro. Also works with hash literals
   and vectors."
  ([x] x)
  ([x form] `(-<>* ~form ~x :last))
  ([x form & forms] `(-<>> (-<>> ~x ~form) ~@forms)))

(defmacro <<-
  "the 'back-arrow'"
  [& forms]
  `(->> ~@(reverse forms)))

(defmacro ^:internal furcula*
  "sugar-free basis of public API"
  [operator parallel? form branches]
  (let [base-form-result (gensym)
        branches (vec branches)]
    `(let [~base-form-result ~form]
       (map ~(if parallel? deref identity)
            ~(cons
              'vector
              (let [branch-forms (for [branch branches]
                                   `(~operator ~base-form-result ~branch))]
                (if parallel?
                  (map (fn [branch-form]
                         `(future ~branch-form)) branch-forms)
                  branch-forms)))))))

(defmacro -<
  "'the furcula': branch one result into multiple flows"
  [form & branches]
  `(furcula* -> nil ~form ~branches))

(defmacro -<:p
  "parallel furcula"
  [form & branches]
  `(furcula* -> :parallel ~form ~branches))

(defmacro -<<
  "'the trystero furcula': analog of ->> for furcula"
  [form & branches]
  `(furcula* ->> nil ~form ~branches))

(defmacro -<<:p
  "parallel trystero furcula"
  [form & branches]
  `(furcula* ->> :parallel ~form ~branches))

(defmacro -<><
  "'the diamond fishing rod': analog of -<> for furcula"
  [form & branches]
  `(furcula* -<> nil ~form ~branches))

(defmacro -<><:p
  "parallel diamond fishing rod"
  [form & branches]
  `(furcula* -<> :parallel ~form ~branches))

(defmacro -<>><
  "'the diamond harpoon': analog of -<>> for furcula"
  [form & branches]
  `(furcula* -<>> nil ~form ~branches))

(defmacro -<>><:p
  "parallel diamond harpoon"
  [form & branches]
  `(furcula* -<>> :parallel ~form ~branches))

(defmacro ^:internal defnilsafe [docstring non-safe-name nil-safe-name]
  `(defmacro ~nil-safe-name ~docstring
     {:arglists '([~'x ~'form] [~'x ~'form ~'& ~'forms])}
     ([x# form#]
        `(let [~'i# ~x#] (when-not (nil? ~'i#) (~'~non-safe-name ~'i# ~form#))))
     ([x# form# & more#]
        `(~'~nil-safe-name (~'~nil-safe-name ~x# ~form#) ~@more#))))

(defnilsafe "the diamond wand version of some->"
  -<> some-<>)

(defnilsafe "the diamond wand version of some->>"
  -<>> some-<>>)

(defmacro apply->>
  "applicative ->>"
  [& forms]
  `(->> ~@(cons (first forms)
                (map #(if (coll? %)
                        (cons 'apply %)
                        (list 'apply %))
                     (rest forms)))))

(defmacro apply->
  "applicative ->"
  [& forms]
  `(-> ~@(cons (first forms)
               (map (fn [el#]
                      (if (coll? el#)
                        `((partial apply ~(first el#)) ~@(rest el#))
                        (list `(partial apply ~el#))))
                    (rest forms)))))

(defmacro -!>
  "non-updating -> for unobtrusive side-effects"
  [form & forms]
  `(let [x# ~form] (-> x# ~@forms) x#))

(defmacro -!>>
  "non-updating ->> for unobtrusive side-effects"
  [form & forms]
  `(let [x# ~form] (->> x# ~@forms) x#))

(defmacro -!<>
  "non-updating -<> for unobtrusive side-effects"
  [form & forms]
  `(let [x# ~form] (-<> x# ~@forms) x#))

(defmacro -!<>>
  "non-updating -<>> for unobtrusive side-effects"
  [form & forms]
  `(let [x# ~form] (-<>> x# ~@forms) x#))

(defmacro macro-maker
  "a wrapper for defining functions with -<> that allows for arbitrary first function arity as well."
  [declarator name & forms]
  (let [start         (list declarator name) 
        docstring     (list (if (string? (first forms)) (first forms) "Generated function."))
        forms*        (if (string? (first forms)) (rest forms) forms)
        arg-form      (if (seq? (first forms*)) '([arg]) '([& args]))
        thread-init   (if (seq? (first forms*)) `(-<> ~'arg) `(-<> (apply ~(first forms*) ~'args)))
        thread-remain (if (seq? (first forms*)) forms* (rest forms*))]
  (concat 
    start
    docstring
    arg-form
    (list (concat thread-init thread-remain)))))

(defmacro =>
  "The Pointless Arrow: a way of more strictly achieving point free code
    by preventing argument declaration. It also allows for arbitrary arity
    and cuts down on boiler plate when -<> is being relied upon heavily."
  [& args]
  (concat `(macro-maker ~'defn) args))

(defmacro +>
  "The Secret Arrow: a private variant of The Pointless Arrow."
  [& args]
  (concat `(macro-maker ~'defn-) args))

(defmacro <<>>
  "The High Diamond: a variant of the pointless arrow that makes macros"
  [& args]
  (concat `(macro-maker ~'defmacro) args))
