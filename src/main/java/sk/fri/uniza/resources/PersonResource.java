package sk.fri.uniza.resources;

import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.*;
import org.hibernate.HibernateException;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.NotEmpty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.fri.uniza.api.City;
import sk.fri.uniza.api.Paged;
import sk.fri.uniza.api.Person;
import sk.fri.uniza.auth.Role;
import sk.fri.uniza.core.User;
import sk.fri.uniza.db.CitiesDao;
import sk.fri.uniza.db.PersonDao;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@SwaggerDefinition(
        securityDefinition = @SecurityDefinition(
                oAuth2Definitions = @OAuth2Definition(description = "oauth2", scopes = {@Scope(name = Role.ADMIN, description = "Access to all resources"), @Scope(name = Role.USER_READ_ONLY, description = "Limited access")}, flow = OAuth2Definition.Flow.ACCESS_CODE, tokenUrl = "http://localhost:8085/backend/login/token", key = "oauth2", authorizationUrl = "http://localhost:8085/backend/login")
        )
)

@Api(value = "Person resource", authorizations = {@Authorization(value = "oauth2", scopes = {@AuthorizationScope(scope = Role.ADMIN, description = "Access to all resources"), @AuthorizationScope(scope = Role.USER_READ_ONLY, description = "Limited access")})})


@ApiImplicitParams(
        @ApiImplicitParam(name = "Authorization", value = "Bearer token", required = false, dataType = "string", paramType = "header")
)
@Path("/persons")
public class PersonResource {
    private final Logger myLogger = LoggerFactory.getLogger(this.getClass());
    private PersonDao personDao;
    private CitiesDao citiesDao;

    public PersonResource(PersonDao personDao, CitiesDao citiesDao) {
        this.personDao = personDao;
        this.citiesDao = citiesDao;
    }

    @GET
    @UnitOfWork
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(Role.ADMIN)

    // Swagger
    @ApiOperation(value = "Obtain list of users", response = Person.class, responseContainer = "List", authorizations = {@Authorization(value = "oauth2", scopes = {@AuthorizationScope(scope = Role.ADMIN, description = "Access to all resources")})})
    public Response getListOfPersons(@QueryParam("limit") Integer limit, @QueryParam("page") Integer page) {
        if (page == null) page = 1;
        if (limit != null) {
            Paged<List<Person>> listPaged = personDao.getAll(limit, page);
            return Response.ok()
                    .entity(listPaged)
                    .build();

        } else {
            List<Person> userList = personDao.getAll();
            return Response.ok()
                    .entity(userList)
                    .build();
        }

    }

    @DELETE
    @UnitOfWork
    @RolesAllowed(Role.ADMIN)
    @ApiOperation(value = "Delete person", response = Person.class, authorizations = {@Authorization(value = "oauth2", scopes = {@AuthorizationScope(scope = Role.ADMIN, description = "Access to all resources")})})
    public Response deletePerson(@ApiParam(hidden = true) @Auth User user, @QueryParam("id") Long id) {
        if (user.getId() != id) {
            Optional<Person> person1 = personDao.findById(id);

            return person1.map(person -> {
                personDao.delete(person);
                return Response.ok().build();
            }).get();

        }
        return Response.status(Response.Status.BAD_REQUEST).build();
    }

    @GET
    @Path("/{id}")
    @UnitOfWork
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({Role.ADMIN, Role.USER_READ_ONLY})
    @ApiOperation(value = "Find person by ID", response = Person.class, authorizations = {@Authorization(value = "oauth2", scopes = {@AuthorizationScope(scope = Role.ADMIN, description = "Access to all resources"), @AuthorizationScope(scope = Role.USER_READ_ONLY, description = "Limited access")})})
    public Person getPersonInfo(@ApiParam(hidden = true) @Auth User user, @PathParam("id") Long id) {

        if (!user.getRoles().contains(Role.ADMIN)) {
            if (user.getId() != id) {
                throw new WebApplicationException(Response.Status.UNAUTHORIZED);
            }
        }

        Optional<Person> person = personDao.findById(id);
        return person.orElseThrow(() -> {
            throw new WebApplicationException("Wrong person ID!", Response.Status.BAD_REQUEST);
        });

    }

    @POST
    @UnitOfWork
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({Role.ADMIN, Role.USER_READ_ONLY})
    @ApiOperation(value = "Save or update person", response = Person.class, authorizations = {@Authorization(value = "oauth2", scopes = {@AuthorizationScope(scope = Role.ADMIN, description = "Access to all resources"), @AuthorizationScope(scope = Role.USER_READ_ONLY, description = "Limited access")})})
    public Person setPersonInfo(@ApiParam(hidden = true) @Auth User user, @Valid Person person) {

        if (!user.getRoles().contains(Role.ADMIN)) {
            if (user.getId() != person.getId()) {
                throw new WebApplicationException(Response.Status.UNAUTHORIZED);
            }
        }

        if (person.getId() == null)
            // If no id is presented, person is saved as new instance
            try {
                personDao.save(person);
            } catch (HibernateException e) {
                throw new WebApplicationException(e.getMessage(), 422); //422 Unprocessable Entity - validation like errors
            }
        else
            personDao.update(person, null);

        return person;
    }

    @POST
    @Path("/password")
    @UnitOfWork
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @RolesAllowed({Role.ADMIN})
    @ApiOperation(value = "Set new password", authorizations = {@Authorization(value = "oauth2", scopes = {@AuthorizationScope(scope = Role.ADMIN, description = "Access to all resources")})})
    public Response setNewPassword(@QueryParam("id") Long id,
                                   @FormParam("password")
                                   @Length(min = 5)
                                   @NotEmpty String password) {

        personDao.saveNewPassword(id, password);
        return Response.ok().build();


    }

    @GET
    @Path("/{id}/cities")
    @UnitOfWork
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
    public Response getListOfPersonsCities(@Auth User user, @PathParam("id") Long id, @QueryParam("limit") Integer limit, @QueryParam("page") Integer page) {
        Person person = checkPermissionsAndGetPerson(user, id);

        Paged<List<City>> cityList = person.getFollowedCities(limit, page);

        return Response.ok()
                .entity(cityList)
                .build();
    }


    @POST
    @Path("/{id}/cities")
    @UnitOfWork
    public Response addPersonCity(@Auth User user, @PathParam("id") Long id, @QueryParam("cityID") Long cityID){

        Person person = checkPermissionsAndGetPerson(user, id);

        Optional<City> cityOptional = citiesDao.findById(cityID);

        City city = cityOptional.orElseThrow(() -> {
            throw new WebApplicationException("Wrong city ID!", Response.Status.BAD_REQUEST);
        });

        person.addFollowedCity(city);

        return Response.ok().build();
    }


    @DELETE
    @Path("/{id}/cities")
    @UnitOfWork
    public Response removePersonCity(@Auth User user, @PathParam("id") Long id, @QueryParam("cityID") Long cityID){

        Person person = checkPermissionsAndGetPerson(user, id);

        Optional<City> cityOptional = citiesDao.findById(cityID);

        City city = cityOptional.orElseThrow(() -> {
            throw new WebApplicationException("Wrong city ID!", Response.Status.BAD_REQUEST);
        });

        person.removeFollowedCity(city);

        return Response.ok().build();
    }


    private Person checkPermissionsAndGetPerson(User user, Long id){
        if (!user.getRoles().contains(Role.ADMIN)) {            // if user is not admin
            if (!Objects.equals(user.getId(), id)) {                           // if user not want his own data
                throw new WebApplicationException(Response.Status.UNAUTHORIZED);
            }
        }

        Optional<Person> personOptional = personDao.findById(id);

        return personOptional.orElseThrow(() -> {
            throw new WebApplicationException("Wrong person ID!", Response.Status.BAD_REQUEST);
        });
    }
}
