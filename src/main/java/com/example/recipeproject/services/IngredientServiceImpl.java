package com.example.recipeproject.services;

import com.example.recipeproject.commands.IngredientCommand;
import com.example.recipeproject.converters.IngredientCommandToIngredient;
import com.example.recipeproject.converters.IngredientToIngredientCommand;
import com.example.recipeproject.domain.Ingredient;
import com.example.recipeproject.domain.Recipe;
import com.example.recipeproject.repositories.RecipeRepository;
import com.example.recipeproject.repositories.UnitOfMeasureRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
public class IngredientServiceImpl implements IngredientService {

    private final IngredientToIngredientCommand ingredientToIngredientCommand;
    private final IngredientCommandToIngredient ingredientCommandToIngredient;
    private final RecipeRepository recipeRepository;
    private final UnitOfMeasureRepository unitOfMeasureRepository;

    public IngredientServiceImpl(IngredientToIngredientCommand ingredientToIngredientCommand,
                                 IngredientCommandToIngredient ingredientCommandToIngredient,
                                 RecipeRepository recipeRepository, UnitOfMeasureRepository unitOfMeasureRepository) {
        this.ingredientToIngredientCommand = ingredientToIngredientCommand;
        this.ingredientCommandToIngredient = ingredientCommandToIngredient;
        this.recipeRepository = recipeRepository;
        this.unitOfMeasureRepository = unitOfMeasureRepository;
    }

    @Override
    public IngredientCommand findByRecipeIdAndIngredientId(Long recipeId, Long ingredientId) {

        Optional<Recipe> recipeOptional = recipeRepository.findById(recipeId);

        if (!recipeOptional.isPresent()){
            //todo impl error handling
            log.error("recipe id not found. Id: " + recipeId);
        }

        Recipe recipe = recipeOptional.get();

        Optional<IngredientCommand> ingredientCommandOptional = recipe.getIngredients().stream()
                .filter(ingredient -> ingredient.getId().equals(ingredientId))
                .map( ingredient -> ingredientToIngredientCommand.convert(ingredient)).findFirst();

        if(!ingredientCommandOptional.isPresent()){
            //todo impl error handling
            log.error("Ingredient id not found: " + ingredientId);
        }

        return ingredientCommandOptional.get();
    }

    @Override
    @Transactional
    public IngredientCommand saveIngredientCommand(IngredientCommand command) {
        // return an Optional Recipe (null or not-null) from the recipeRepository, searching by the id from the passed command object
        Optional<Recipe> recipeOptional = recipeRepository.findById(command.getRecipeId());

        // if a matching Recipe is NOT found
        if(!recipeOptional.isPresent()){
            // log a "not found" error
            //todo toss error if not found!
            log.error("Recipe not found for id: " + command.getRecipeId());

            // return a NEW IngredientCommand object
            return new IngredientCommand();

            // if a matching Recipe IS found
        } else {
            // get the Recipe
            Recipe recipe = recipeOptional.get();

            // Get an Optional list of ingredient objects from the recipe object
            Optional<Ingredient> ingredientOptional = recipe
                    // get those ingredients
                    .getIngredients()
                    // as a stream
                    .stream()
                    // filter them, finding the ingredient that matches the id of the command object
                    .filter(ingredient -> ingredient.getId().equals(command.getId()))
                    // grab the first instance where that match occurs
                    .findFirst();

            // if the optional ingredient IS found
            if(ingredientOptional.isPresent()){
                // get the ingredient
                Ingredient ingredientFound = ingredientOptional.get();
                // set the description of that ingredient (with the value from the command object)
                ingredientFound.setDescription(command.getDescription());
                // set the amount of the ingredient (with the value from the command object)
                ingredientFound.setAmount(command.getAmount());
                // set the Unit of Measure
                ingredientFound.setUom(unitOfMeasureRepository
                        // by matching the correct uom with the id value from the command object
                        .findById(command.getUom().getId())
                        // if no uom is found, throw an exception
                        .orElseThrow(() -> new RuntimeException("UOM NOT FOUND"))); //todo address this
            } else {
                //if the ingredient is NOT found, add new Ingredient by converting the command ingredient into an actual ingredient
                Ingredient ingredient = ingredientCommandToIngredient.convert(command);
                // set the associated recipe for the ingredient
                ingredient.setRecipe(recipe);
                // add the new ingredient to the recipe
                recipe.addIngredient(ingredient);
            }

            // persist the Recipe with updated or added ingredient in the dB
            Recipe savedRecipe = recipeRepository.save(recipe);

            Optional<Ingredient> savedIngredientOptional = savedRecipe.getIngredients().stream()
                    .filter(recipeIngredients -> recipeIngredients.getId().equals(command.getId()))
                    .findFirst();

            //if the ingredient is NOT present
            if(!savedIngredientOptional.isPresent()){
                //not totally safe... But best guess
                savedIngredientOptional = savedRecipe.getIngredients().stream()
                        .filter(recipeIngredients -> recipeIngredients.getDescription().equals(command.getDescription()))
                        .filter(recipeIngredients -> recipeIngredients.getAmount().equals(command.getAmount()))
                        .filter(recipeIngredients -> recipeIngredients.getUom().getId().equals(command.getUom().getId()))
                        .findFirst();
            }

            //to do check for fail
            return ingredientToIngredientCommand.convert(savedIngredientOptional.get());
        }

    }

    @Override
    public void deleteById(Long recipeId, Long idToDelete) {

        log.debug("Deleting ingredient: " + recipeId + ":" + idToDelete);

        Optional<Recipe> recipeOptional = recipeRepository.findById(recipeId);

        if(recipeOptional.isPresent()){
            Recipe recipe = recipeOptional.get();
            log.debug("found recipe");

            Optional<Ingredient> ingredientOptional = recipe
                    .getIngredients()
                    .stream()
                    .filter(ingredient -> ingredient.getId().equals(idToDelete))
                    .findFirst();

            if(ingredientOptional.isPresent()){
                log.debug("found Ingredient");
                Ingredient ingredientToDelete = ingredientOptional.get();
                ingredientToDelete.setRecipe(null);
                recipe.getIngredients().remove(ingredientOptional.get());
                recipeRepository.save(recipe);
            }
        } else {
            log.debug("Recipe Id Not found. Id:" + recipeId);
        }
    }
}