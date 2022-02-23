[![](https://jitpack.io/v/Kobanister/ViewBindingAnnotations.svg)](https://jitpack.io/#Kobanister/ViewBindingAnnotations)


# ViewBindingAnnotations

This is a small library that will simplify the viewBinding usage.
It will be helpful for large projects or just to avoid binding initialization in every activity/fragment via using the generics and passing the binding class to your BaseActivity/BaseFragment class.


## Install

### Gradle Dependency (Module level)

Since it uses the AnnotationProcessor under the hood you need to add kapt:

```
plugins {
    ...
    id 'kotlin-kapt'
}
```

Enable the ***viewBinging***, of course:

```
android {
    ...
    buildFeatures {
        viewBinding true
    }
}
```

And add the dependency:

```
dependencies {
    ...
    implementation 'com.github.Kobanister:ViewBindingAnnotations:$latestVersion'
    kapt 'com.github.Kobanister:ViewBindingAnnotations:$latestVersion'
}
```


## Usage

The library contains annotations for fragment (`@BindFragment`) and activity (`@BindActivity`).

### Activity usage

1. Add `@BindActivity` annotation to you Activity class:

```
@BindActivity
class MainActivity : BaseActivity<ActivityMainBinding>() {}
```

2. Add the logic to the `BaseActivity`:

```
    private var _binding: VB? = null
    open val binding: VB
        get() = _binding ?: throw Throwable("Binding must not be null")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ...
        this._binding = BindingFactory.getBinding(this, null)
        setContentView(binding.root)
    }
```

3. You are ready to use the `binding` in your Activity:

```
    binding.tvTitle.text = "Hello Binding! (From Activity)"
```


### Fragment usage

1. Add `@BindFragment` annotation to you Fragment class:

```
@BindFragment
class MainFragment : BaseFragment<FragmentMainBinding>() {}
```

2. Add the logic to the `BaseFragment`:

```
    private var _binding: VB? = null
    protected open val binding: VB
        get() = _binding ?: throw Throwable("Binding must not be null")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        ...
        this._binding = BindingFactory.getBinding(this, container)
        return binding.root
    }

    override fun onDestroyView() {
        ...
        _binding = null
        super.onDestroyView()
    }
```

3. You are ready to use the `binding` in your Fragment:

```
    binding.tvTitle.text = "Hello Binding! (From Fragment)"
```


## TODO LIST

* [ ] Add extensions for the `DialogFragment` and `RecyclerView.ViewHolder`


## Honorable mentions

Many thanks to [@aengussong](https://github.com/aengussong) for helping me out :hugs:


# License
Please see [LICENSE](https://github.com/Kobanister/ViewBindingAnnotations/blob/master/LICENSE)
