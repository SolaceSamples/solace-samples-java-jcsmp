# Solace Samples Template

This project is the common template from which all Solace Samples are merged. When creating a new Solace sample, you should fork this repo and then create your sample. Template updates can be applied by merging from this repo on both the `master` and `gh-pages` branch. The `gh-pages` branch is setup and ready to be used to create tutorials. See the [README](https://github.com/SolaceSamples/solace-samples-template/blob/gh-pages/README.md) in that branch for details. Any code and description in the samples should not overlap with this template.

## Instructions (to be deleted in a Solace Samples project)

Here are some instructions once you've forked the repository and are creating new Solace samples.

1. Update the repository links in [](CONTRIBUTING.md)
2. Add your Samples source code to the master branch
3. Update this README with instructions on how to build and run.
4. Create walk through tutorials in the docs directory. Specifically by modifying `_config.yml`, `_data/tutorials.yml`, and `_tutorials/...` 

To merge changes to a Samples project from the template, you would use the following commands:

    git remote add samples-template https://github.com/SolaceSamples/solace-samples-template.git
    git fetch samples-template
    git merge samples-template/gh-pages
    git remote remove samples-template

Below this are common sections that should appear in all Solace Samples README.md. Leave them! :)

## Exploring the Samples

### Setting up your preferred IDE

Using a modern Java IDE provides cool productivity features like auto-completion, on-the-fly compilation, assisted refactoring and debugging which can be useful when you're exploring the samples and even modifying the samples. Follow the steps below for your preferred IDE.

#### Using Eclipse

To generate Eclipse metadata (.classpath and .project files), do the following:

    ./gradlew eclipse

Once complete, you may then import the projects into Eclipse as usual:

 *File -> Import -> Existing projects into workspace*

Browse to the *'solace-samples-java'* root directory. All projects should import
free of errors.

#### Using IntelliJ IDEA

To generate IDEA metadata (.iml and .ipr files), do the following:

    ./gradlew idea

## Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md) for details on our code of conduct, and the process for submitting pull requests to us.

## Authors

See the list of [contributors](https://github.com/SolaceSamples/solace-samples-template/contributors) who participated in this project.

## License

This project is licensed under the Apache License, Version 2.0. - See the [LICENSE](LICENSE) file for details.

## Resources

For more information try these resources:

- The Solace Developer Portal website at:
[http://dev.solacesystems.com](http://dev.solacesystems.com/)
- Get a better understanding of [Solace technology.](http://dev.solacesystems.com/tech/)
- Check out the [Solace blog](http://dev.solacesystems.com/blog/) for other interesting discussions around Solace technology
- Ask the [Solace community.](http://dev.solacesystems.com/community/)
