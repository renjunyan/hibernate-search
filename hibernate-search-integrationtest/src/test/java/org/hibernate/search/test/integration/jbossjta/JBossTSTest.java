package org.hibernate.search.test.integration.jbossjta;

import java.util.HashMap;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.SharedCacheMode;
import javax.persistence.ValidationMode;
import javax.persistence.spi.PersistenceUnitTransactionType;
import javax.sql.XADataSource;

import com.arjuna.ats.arjuna.coordinator.TxControl;
import com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionManagerImple;
import com.arjuna.ats.jdbc.TransactionalDriver;
import org.apache.lucene.search.Query;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import org.hibernate.ConnectionReleaseMode;
import org.hibernate.cfg.Environment;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.ejb.HibernatePersistence;
import org.hibernate.search.jpa.FullTextEntityManager;
import org.hibernate.search.jpa.Search;
import org.hibernate.search.query.dsl.v2.QueryBuilder;
import org.hibernate.search.test.integration.jbossjta.infra.H2dataSourceProvider;
import org.hibernate.search.test.integration.jbossjta.infra.JBossTSStandaloneTransactionManagerLookup;
import org.hibernate.search.test.integration.jbossjta.infra.PersistenceUnitInfoBuilder;
import org.hibernate.search.test.integration.jbossjta.infra.ReadOnlyPersistenceUnitInfo;
import org.hibernate.search.test.integration.jbossjta.infra.XADataSourceWrapper;
import org.hibernate.search.util.FileHelper;

/**
 * @author Emmanuel Bernard
 */

public class JBossTSTest {

	private static EntityManagerFactory factory;

	@BeforeClass
	public static void setUp() throws Exception {
		TxControl.setDefaultTimeout(0);
		H2dataSourceProvider dsProvider = new H2dataSourceProvider();
		final XADataSource h2DataSource = dsProvider.getDataSource( dsProvider.getDataSourceName() );
		XADataSourceWrapper dsw = new XADataSourceWrapper(
				dsProvider.getDataSourceName(),
				h2DataSource
		);
		dsw.setProperty( TransactionalDriver.dynamicClass, H2dataSourceProvider.class.getName() );
		dsw.setProperty( TransactionalDriver.userName, "sa" );
		dsw.setProperty( TransactionalDriver.password, "" );

		PersistenceUnitInfoBuilder pub = new PersistenceUnitInfoBuilder();
		final ReadOnlyPersistenceUnitInfo unitInfo = pub
				.setExcludeUnlistedClasses( true )
				.setJtaDataSource( dsw )
				//.setJtaDataSource( ( DataSource) h2DataSource )
				.setPersistenceProviderClassName( HibernatePersistence.class.getName() )
				.setPersistenceUnitName( "jbossjta" )
				.setPersistenceXMLSchemaVersion( "2.0" )
				.setSharedCacheMode( SharedCacheMode.NONE )
				.setValidationMode( ValidationMode.NONE )
				.setTransactionType( PersistenceUnitTransactionType.JTA )
				.addManagedClassNames( Tweet.class.getName() )
						//.addProperty( "hibernate.transaction.factory_class", null )
				.addProperty(
						"hibernate.transaction.manager_lookup_class",
						JBossTSStandaloneTransactionManagerLookup.class.getName()
				)
				.addProperty( "hibernate.dialect", H2Dialect.class.getName() )
				.addProperty( Environment.HBM2DDL_AUTO, "create-drop" )
				.addProperty( Environment.SHOW_SQL, "true" )
				//I don't pool connections by JTA transaction. Leave the work to Hibernate Core
				.addProperty( Environment.RELEASE_CONNECTIONS, ConnectionReleaseMode.AFTER_TRANSACTION.toString() )
				.addProperty( "hibernate.search.default.directory_provider", "org.hibernate.search.store.RAMDirectoryProvider" )
				.create();
		final HibernatePersistence hp = new HibernatePersistence();
		factory = hp.createContainerEntityManagerFactory( unitInfo, new HashMap( ) );

	}

	@AfterClass
	public static void tearDown() {
		factory.close();
	}

	@Test
	public void testJBossTS() throws Exception {
		TransactionManagerImple tm = new TransactionManagerImple();
		tm.begin();
		EntityManager em = factory.createEntityManager();
		Tweet tweet = new Tweet( "Spice is the essence of life" );
		em.persist( tweet );
		tm.commit();
		em.close();

		tm.begin();
		em = factory.createEntityManager();
		FullTextEntityManager ftem = Search.getFullTextEntityManager( em );
		final QueryBuilder builder = ftem.getSearchFactory().buildQueryBuilder().forEntity( Tweet.class ).get();
		final Query query = builder
				.keyword()
				.onField( "text" )
				.matching( "spice" )
				.createQuery();

		ftem.createFullTextQuery( query, Tweet.class ).getResultList();
		final List resultList = em.createQuery( "from " + Tweet.class.getName() ).getResultList();
		Assert.assertEquals( 1, resultList.size() );
		for (Object o : resultList) {
			em.remove( o );
		}
		tm.commit();

		em.close();
	}
}
